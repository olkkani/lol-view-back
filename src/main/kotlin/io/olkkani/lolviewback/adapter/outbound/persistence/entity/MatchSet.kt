package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "match_sets")
class MatchSet(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    var setApiId: String,
    var setNumber: Int,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_win_club_id")
    val club: Club,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    val match: Match


)


enum class MatchSetState {
    UNSTARTED,
    IN_PROGRESS,
    COMPLETED,
    UNNEEDED,
}