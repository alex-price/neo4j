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

import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;

import org.neo4j.logging.Log;

public class MembershipListenerAdapter implements MembershipListener
{
    private final CoreTopologyService.Listener listener;
    private final Log log;

    MembershipListenerAdapter( CoreTopologyService.Listener listener, Log log )
    {
        this.listener = listener;
        this.log = log;
    }

    @Override
    public void memberAdded( MembershipEvent membershipEvent )
    {
        log.info( "Member added %s", membershipEvent );
        listener.onTopologyChange();
    }

    @Override
    public void memberRemoved( MembershipEvent membershipEvent )
    {
        log.info( "Member removed %s", membershipEvent );
        listener.onTopologyChange();
    }

    @Override
    public void memberAttributeChanged( MemberAttributeEvent memberAttributeEvent )
    {
    }
}
