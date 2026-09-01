package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "match_participants")
class MatchParticipant(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    var isWin: Boolean?,
    var score: Int = 0,
    @ManyToOne @JoinColumn(name = "match_id")
    var match: Match,

    @ManyToOne @JoinColumn(name = "club_id")
    var club: Club,

    @ManyToOne @JoinColumn(name = "club_profile_id")
    var clubProfile: ClubProfile,

)