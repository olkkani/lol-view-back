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
    @ManyToOne @JoinColumn(name = "match_id")
    var match: Match,

    @ManyToOne @JoinColumn(name = "club_id")
    var club: Club,

    @ManyToOne @JoinColumn(name = "club_profile_id")
    var clubProfile: ClubProfile,

)