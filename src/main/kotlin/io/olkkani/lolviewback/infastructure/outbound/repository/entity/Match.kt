package io.olkkani.lolviewback.infastructure.outbound.repository.entity

import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "matches")
class Match(
    @Id @Tsid
    var id: Long? = null,
    var startTime: ZonedDateTime,

    @Enumerated(EnumType.STRING)
    var matchType: MatchType,

    @Enumerated(EnumType.STRING)
    var matchState: MatchState,
    var matchLabel: String,
    var matchApiId: String,

    @ManyToOne @JoinColumn(name = "tournament_id")
    var tournament: Tournament,
)

enum class MatchType {
    BO3,
    BO5
}

enum class MatchState {
    SCHEDULED,
    ONGOING,
    FINISHED,
}