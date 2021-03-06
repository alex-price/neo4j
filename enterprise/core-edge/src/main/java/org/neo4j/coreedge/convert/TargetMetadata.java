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
package org.neo4j.coreedge.convert;

import java.util.Objects;

import org.neo4j.kernel.impl.store.StoreId;

public class TargetMetadata
{
    private final StoreId before;
    private final long lastTxId;

    public TargetMetadata( StoreId before, long lastTxId )
    {
        this.before = before;
        this.lastTxId = lastTxId;
    }

    @Override
    public String toString()
    {
        return String.format( "TargetMetadata{before=%s, lastTxId=%d}", before, lastTxId );
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
        TargetMetadata that = (TargetMetadata) o;
        return lastTxId == that.lastTxId && storeIdEquals( before, that.before );
    }

    @Override
    public int hashCode()
    {
        int result = 31 + (this.before == null ? 0 : before.theRealHashCode());
        return 31 * result + Objects.hash( lastTxId );
    }

    private boolean storeIdEquals( StoreId one, StoreId two)
    {
        return (one == two || (one != null && one.theRealEquals( two )));
    }

    public StoreId before()
    {
        return before;
    }

    public long lastTxId()
    {
        return lastTxId;
    }
}
