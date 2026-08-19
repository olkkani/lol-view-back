package io.olkkani.lolviewback.infastructure.outbound.repository.entity

import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Id @Tsid
    var id: Long,
)