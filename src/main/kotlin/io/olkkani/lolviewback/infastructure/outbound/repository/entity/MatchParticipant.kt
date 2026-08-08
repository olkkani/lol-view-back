package io.olkkani.lolviewback.infastructure.outbound.repository.entity

import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity
class MatchParticipant(
    @Id @Tsid
    var id: Long? = null,
    var isWin: Boolean?,
    var score: Int = 0,

    @ManyToOne @JoinColumn(name = "match_id")
    var match: Match,

    @ManyToOne @JoinColumn(name = "club_id")
    var club: Club,

    @ManyToOne @JoinColumn(name = "club_profile_id")
    var clubProfile: ClubProfile,

)