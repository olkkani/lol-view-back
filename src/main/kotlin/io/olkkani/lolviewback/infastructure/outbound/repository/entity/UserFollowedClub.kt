package io.olkkani.lolviewback.infastructure.outbound.repository.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "user_followed_clubs")
class UserFollowedClub(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User,
    var clubId: Long,
)