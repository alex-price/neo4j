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
package org.neo4j.coreedge.raft.roles;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import org.neo4j.coreedge.catchup.storecopy.LocalDatabase;
import org.neo4j.coreedge.raft.RaftMessages;
import org.neo4j.coreedge.raft.log.InMemoryRaftLog;
import org.neo4j.coreedge.raft.log.RaftLogEntry;
import org.neo4j.coreedge.raft.outcome.Outcome;
import org.neo4j.coreedge.raft.state.RaftState;
import org.neo4j.coreedge.server.CoreMember;
import org.neo4j.logging.Log;
import org.neo4j.logging.NullLogProvider;

import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.neo4j.coreedge.raft.TestMessageBuilders.heartbeat;
import static org.neo4j.coreedge.raft.roles.AppendEntriesRequestTest.ContentGenerator.content;
import static org.neo4j.coreedge.raft.state.RaftStateBuilder.raftState;
import static org.neo4j.coreedge.server.RaftTestMember.member;

@RunWith(Parameterized.class)
public class HeartbeatTest
{
    private final LocalDatabase storeId = mock( LocalDatabase.class);

    @Parameterized.Parameters(name = "{0} with leader {1} terms ahead.")
    public static Collection<Object[]> data()
    {
        return Arrays.asList( new Object[][]{
                {Role.FOLLOWER, 0}, {Role.FOLLOWER, 1}, {Role.LEADER, 1}, {Role.CANDIDATE, 1}
        } );
    }

    @Parameterized.Parameter(value = 0)
    public Role role;

    @Parameterized.Parameter(value = 1)
    public int leaderTermDifference;

    private CoreMember myself = member( 0 );
    private CoreMember leader = member( 1 );

    @Test
    public void shouldNotResultInCommitIfReferringToFutureEntries() throws Exception
    {
        InMemoryRaftLog raftLog = new InMemoryRaftLog();
        RaftState state = raftState()
                .myself( myself )
                .entryLog( raftLog )
                .build();

        long leaderTerm = state.term() + leaderTermDifference;
        raftLog.append( new RaftLogEntry( leaderTerm, content() ) );

        RaftMessages.Heartbeat heartbeat = heartbeat()
                .from( leader )
                .commitIndex( raftLog.appendIndex() + 1) // The leader is talking about committing stuff we don't know about
                .commitIndexTerm( leaderTerm ) // And is in the same term
                .leaderTerm( leaderTerm )
                .build();

        Outcome outcome = role.handler.handle( heartbeat, state, log(), storeId );

        assertThat( outcome.getLogCommands(), empty());
    }

    @Test
    public void shouldNotResultInCommitIfHistoryMismatches() throws Exception
    {
        InMemoryRaftLog raftLog = new InMemoryRaftLog();
        RaftState state = raftState()
                .myself( myself )
                .entryLog( raftLog )
                .build();

        long leaderTerm = state.term() + leaderTermDifference;
        raftLog.append( new RaftLogEntry( leaderTerm, content() ) );

        RaftMessages.Heartbeat heartbeat = heartbeat()
                .from( leader )
                .commitIndex( raftLog.appendIndex()) // The leader is talking about committing stuff we don't know about
                .commitIndexTerm( leaderTerm ) // And is in the same term
                .leaderTerm( leaderTerm )
                .build();

        Outcome outcome = role.handler.handle( heartbeat, state, log(), storeId );

        assertThat( outcome.getCommitIndex(), Matchers.equalTo(0L) );
    }

    @Test
    public void shouldResultInCommitIfHistoryMatches() throws Exception
    {
        InMemoryRaftLog raftLog = new InMemoryRaftLog();
        RaftState state = raftState()
                .myself( myself )
                .entryLog( raftLog )
                .build();

        long leaderTerm = state.term() + leaderTermDifference;
        raftLog.append( new RaftLogEntry( leaderTerm - 1, content() ) );

        RaftMessages.Heartbeat heartbeat = heartbeat()
                .from( leader )
                .commitIndex( raftLog.appendIndex()) // The leader is talking about committing stuff we don't know about
                .commitIndexTerm( leaderTerm ) // And is in the same term
                .leaderTerm( leaderTerm )
                .build();

        Outcome outcome = role.handler.handle( heartbeat, state, log(), storeId );

        assertThat( outcome.getLogCommands(), empty() );

    }

    private Log log()
    {
        return NullLogProvider.getInstance().getLog( getClass() );
    }

}
