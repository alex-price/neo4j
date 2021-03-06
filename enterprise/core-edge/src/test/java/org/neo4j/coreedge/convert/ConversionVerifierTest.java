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

import org.junit.Test;

import org.neo4j.kernel.impl.store.StoreId;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;

public class ConversionVerifierTest
{
    @Test
    public void shouldAcceptValidConvertibleMetadata() throws Exception
    {
        // given
        ConversionVerifier verifier = new ConversionVerifier();

        StoreId before = new StoreId( 1, 2, 3, 4, 5 );
        StoreId after = new StoreId( 6, 7, 8, 9, 10 );
        long transactionId = 44L;

        SourceMetadata metadata = new SourceMetadata( before, after, transactionId );
        TargetMetadata targetMetadata = new TargetMetadata( before, transactionId );

        // when
        verifier.conversionGuard( metadata, targetMetadata );

        // then happily convert
    }

    @Test
    public void shouldRejectIfStoreIdDoesNotMatch() throws Exception
    {
        // given
        ConversionVerifier verifier = new ConversionVerifier();

        StoreId before = new StoreId( 1, 2, 3, 4, 5 );
        StoreId after = new StoreId( 6, 7, 8, 9, 10 );
        long transactionId = 44L;

        SourceMetadata metadata = new SourceMetadata( before, after, transactionId );
        TargetMetadata targetMetadata = new TargetMetadata( new StoreId( 9, 9, 9, 9, 9 ), transactionId );

        // when
        try
        {
            verifier.conversionGuard( metadata, targetMetadata );
            fail("Should not have been able to convert");
        }
        catch ( Exception e )
        {
            assertThat( e.getMessage(), containsString( "" ) );

            // then
            assertThat( e.getMessage(), containsString( "Cannot convert store" ) );
        }

    }

    @Test
    public void shouldRejectIfLastTxDoesNotMatch() throws Exception
    {
        // given
        ConversionVerifier verifier = new ConversionVerifier();

        StoreId before = new StoreId( 1, 2, 3, 4, 5 );
        StoreId after = new StoreId( 6, 7, 8, 9, 10 );
        long transactionId = 44L;

        SourceMetadata metadata = new SourceMetadata( before, after, transactionId );
        TargetMetadata targetMetadata = new TargetMetadata( before, 999L );

        // when
        try
        {
            verifier.conversionGuard( metadata, targetMetadata );
            fail( "Should not have been able to convert" );
        }
        catch ( Exception e )
        {
            // then
            assertThat( e.getMessage(), containsString( "Cannot convert store" ) );
        }
    }
}
