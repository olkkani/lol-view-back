package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "matches")
class Match(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
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
    UNSTARTED,
    COMPLETED,
    IN_PROGRESS,
}

