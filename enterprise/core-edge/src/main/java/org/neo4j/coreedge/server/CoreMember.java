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

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import org.neo4j.coreedge.raft.state.StateMarshal;
import org.neo4j.storageengine.api.ReadableChannel;
import org.neo4j.storageengine.api.WritableChannel;

import static java.lang.String.format;

public class CoreMember
{
    private final UUID uuid;

    public CoreMember( UUID uuid )
    {
        Objects.requireNonNull( uuid );
        this.uuid = uuid;
    }

    public UUID getUuid()
    {
        return uuid;
    }

    @Override
    public String toString()
    {
        return format( "CoreMember{uuid=%s}", uuid );
    }

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        CoreMember that = (CoreMember) o;
        return Objects.equals( uuid, that.uuid );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( uuid );
    }

    public static class CoreMemberMarshal implements StateMarshal<CoreMember>
    {
        @Override
        public void marshal( CoreMember member, WritableChannel channel ) throws IOException
        {
            if ( member == null )
            {
                channel.put( (byte) 0 );
            }
            else
            {
                channel.put( (byte) 1 );
                channel.putLong( member.uuid.getMostSignificantBits() );
                channel.putLong( member.uuid.getLeastSignificantBits() );
            }
        }

        @Override
        public CoreMember unmarshal( ReadableChannel source ) throws IOException
        {
            byte marker = source.get();
            if ( marker == 0 )
            {
                return null;
            }
            else
            {
                long mostSigBits = source.getLong();
                long leastSigBits = source.getLong();
                return new CoreMember( new UUID( mostSigBits, leastSigBits ) );
            }
        }

        @Override
        public CoreMember startState()
        {
            return null;
        }

        @Override
        public long ordinal( CoreMember coreMember )
        {
            return coreMember == null ? 0 : 1;
        }
    }
}
