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
package org.neo4j.coreedge.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RaftTestMember
{
    private static final Map<Integer, CoreMember> testMembers = new HashMap<>();

    public static CoreMember member( int id )
    {
        CoreMember member = testMembers.get( id );
        if ( member == null ) {
            member = new CoreMember( UUID.randomUUID() );
            testMembers.put( id, member );
        }
        return member;
    }
}
