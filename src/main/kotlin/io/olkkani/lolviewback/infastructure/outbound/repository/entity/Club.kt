package io.olkkani.lolviewback.infastructure.outbound.repository.entity

import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Date
import java.time.LocalDate
import java.time.ZonedDateTime

@Entity
@Table(name = "clubs")
class Club(
    @Id @Tsid
    var id: Long? = null,
    var isActive: Boolean,
    var foundedDate: LocalDate? = null,
    var disbandedDate: LocalDate? = null,
)