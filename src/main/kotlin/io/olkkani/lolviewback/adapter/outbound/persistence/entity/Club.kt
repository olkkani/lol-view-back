package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "clubs")
class Club(
    @Id
    var id: Long = TSID.Factory.getTsid().toLong(),
    var isActive: Boolean,
    var foundedDate: LocalDate? = null,
    var disbandedDate: LocalDate? = null,
)