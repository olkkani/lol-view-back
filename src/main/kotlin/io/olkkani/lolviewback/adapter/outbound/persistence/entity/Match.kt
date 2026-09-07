package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import com.nimbusds.openid.connect.sdk.assurance.claims.ISO3166_1Alpha2CountryCode.BO
import io.hypersistence.tsid.TSID
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType.BO1
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType.BO3
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.MatchType.BO5
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
    BO1,
    BO3,
    BO5;
    companion object {
        fun fromBestOfCount(count: Int): MatchType = when(count){
            1 -> BO1
            3 -> BO3
            5 -> BO5
            else -> throw IllegalArgumentException("not support match type value: $count")
        }
    }
}

enum class MatchState {
    UNSTARTED,
    COMPLETED,
    IN_PROGRESS;
    companion object {
        fun fromState(state: String): MatchState = when(state){
            "unstarted" -> UNSTARTED
            "completed" -> COMPLETED
            "inProgress" -> IN_PROGRESS
            else -> throw IllegalArgumentException("not support match state value: $state")
        }
    }
}

