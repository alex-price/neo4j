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

import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MemberAttributeConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.MembershipListener;
import com.hazelcast.instance.GroupProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.neo4j.coreedge.server.AdvertisedSocketAddress;
import org.neo4j.coreedge.server.CoreEdgeClusterSettings;
import org.neo4j.coreedge.server.CoreMember;
import org.neo4j.coreedge.server.ListenSocketAddress;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.logging.Log;
import org.neo4j.logging.LogProvider;

class HazelcastServerLifecycle extends LifecycleAdapter implements CoreTopologyService
{
    private final Config config;
    private final CoreMember myself;
    private final LogProvider logProvider;
    private final Log log;
    private HazelcastInstance hazelcastInstance;

    private List<MembershipListener> membershipListeners = new ArrayList<>();
    private Map<MembershipListener, String> membershipRegistrationId = new ConcurrentHashMap<>();

    HazelcastServerLifecycle( Config config, CoreMember myself, LogProvider logProvider )
    {
        this.config = config;
        this.myself = myself;
        this.logProvider = logProvider;
        this.log = logProvider.getLog( getClass() );
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
    public void start()
    {
        hazelcastInstance = createHazelcastInstance();
        log.info( "Cluster discovery service started" );

        for ( MembershipListener membershipListener : membershipListeners )
        {
            String registrationId = hazelcastInstance.getCluster().addMembershipListener( membershipListener );
            membershipRegistrationId.put( membershipListener, registrationId );
        }
    }

    @Override
    public void stop()
    {
        try
        {
            hazelcastInstance.shutdown();
        }
        catch ( Throwable e )
        {
            log.warn( "Failed to stop Hazelcast", e );
        }
    }

    private HazelcastInstance createHazelcastInstance()
    {
        System.setProperty( GroupProperties.PROP_WAIT_SECONDS_BEFORE_JOIN, "1" );

        JoinConfig joinConfig = new JoinConfig();
        joinConfig.getMulticastConfig().setEnabled( false );
        TcpIpConfig tcpIpConfig = joinConfig.getTcpIpConfig();
        tcpIpConfig.setEnabled( true );

        List<AdvertisedSocketAddress> initialMembers = config.get( CoreEdgeClusterSettings.initial_core_cluster_members );
        for ( AdvertisedSocketAddress address : initialMembers )
        {
            tcpIpConfig.addMember( address.toString() );
        }
        log.info( "Discovering cluster with initial members: " + initialMembers );

        NetworkConfig networkConfig = new NetworkConfig();
        ListenSocketAddress address = config.get( CoreEdgeClusterSettings.cluster_listen_address );
        networkConfig.setPort( address.socketAddress().getPort() );
        networkConfig.setJoin( joinConfig );

        com.hazelcast.config.Config c = new com.hazelcast.config.Config( );
        c.setProperty( GroupProperties.PROP_INITIAL_MIN_CLUSTER_SIZE,
                String.valueOf( minimumClusterSizeThatCanTolerateOneFaultForExpectedClusterSize() ) );
        c.setProperty( GroupProperties.PROP_LOGGING_TYPE, "none" );

        c.setNetworkConfig( networkConfig );
        c.getGroupConfig().setName( config.get( CoreEdgeClusterSettings.cluster_name ) );

        MemberAttributeConfig memberAttributeConfig = HazelcastClusterTopology.buildMemberAttributes( myself, config );

        c.setMemberAttributeConfig( memberAttributeConfig );

        return Hazelcast.newHazelcastInstance( c );
    }

    private Integer minimumClusterSizeThatCanTolerateOneFaultForExpectedClusterSize()
    {
        return config.get( CoreEdgeClusterSettings.expected_core_cluster_size ) / 2 + 1;
    }

    @Override
    public ClusterTopology currentTopology()
    {
        ClusterTopology clusterTopology = HazelcastClusterTopology.fromHazelcastInstance( hazelcastInstance, logProvider );
        log.info( "Current topology is %s.", clusterTopology );
        return clusterTopology;
    }
}
