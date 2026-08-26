package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "user_identities")
class UserIdentity(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    @Column(name = "user_id")
    var userId: Long,
    var provider: String,
    @Column(name = "provider_user_id")
    var providerUserId: String,
)
