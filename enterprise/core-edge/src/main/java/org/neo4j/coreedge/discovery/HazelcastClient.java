/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.coreedge.discovery;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.core.MembershipListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.neo4j.coreedge.server.AdvertisedSocketAddress;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.logging.Log;
import org.neo4j.logging.LogProvider;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.neo4j.coreedge.discovery.HazelcastClusterTopology.EDGE_SERVERS;

class HazelcastClient extends LifecycleAdapter implements EdgeTopologyService
{
    private final Log log;
    private HazelcastConnector connector;
    private final LogProvider logProvider;
    private HazelcastInstance hazelcastInstance;

    private List<MembershipListener> membershipListeners = new ArrayList<>();
    private Map<MembershipListener, String> membershipRegistrationId = new ConcurrentHashMap<>();

    HazelcastClient( HazelcastConnector connector, LogProvider logProvider )
    {
        this.connector = connector;
        this.logProvider = logProvider;
        log = logProvider.getLog( getClass() );
    }

    @Override
    public void addMembershipListener( Listener listener )
    {
        MembershipListenerAdapter hazelcastListener = new MembershipListenerAdapter( listener, log );
        membershipListeners.add( hazelcastListener );

        if ( hazelcastInstance != null )
        {
            String registrationId = hazelcastInstance.getCluster().addMembershipListener( hazelcastListener );
            membershipRegistrationId.put( hazelcastListener, registrationId );
        }
        listener.onTopologyChange();
    }

    @Override
    public void removeMembershipListener( Listener listener )
    {
        MembershipListenerAdapter hazelcastListener = new MembershipListenerAdapter( listener, log );
        membershipListeners.remove( hazelcastListener );
        String registrationId = membershipRegistrationId.remove( hazelcastListener );

        if ( hazelcastInstance != null && registrationId != null )
        {
            hazelcastInstance.getCluster().removeMembershipListener( registrationId );
        }
    }

    @Override
    public ClusterTopology currentTopology()
    {
        ClusterTopology clusterTopology = new ClusterTopology( false, emptyMap(), emptySet() );
        boolean attemptedConnection = false;

        while ( clusterTopology.coreMembers().isEmpty() && !attemptedConnection )
        {
            if ( hazelcastInstance == null )
            {
                try
                {
                    attemptedConnection = true;
                    hazelcastInstance = connector.connectToHazelcast();
                }
                catch ( IllegalStateException e )
                {
                    log.info( "Unable to connect to core cluster" );
                    break;
                }
            }

            try
            {
                clusterTopology = HazelcastClusterTopology.fromHazelcastInstance( hazelcastInstance, logProvider );
            }
            catch ( HazelcastInstanceNotActiveException e )
            {
                hazelcastInstance = null;
            }
        }

        return clusterTopology;
    }

    @Override
    public void stop() throws Throwable
    {
        if ( hazelcastInstance != null )
        {
            hazelcastInstance.shutdown();
        }
    }

    @Override
    public void registerEdgeServer( AdvertisedSocketAddress address )
    {
        hazelcastInstance.getSet( EDGE_SERVERS ).add( address.toString() );
    }
}
