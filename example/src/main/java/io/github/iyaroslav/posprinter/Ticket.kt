package io.github.iyaroslav.posprinter

data class Ticket(
    var ticketNumber: Int = 1,
    var raw: String? = null,
    var garson: String? = null,
    var table: String? = null,
    var sum: Double? = null,
    var service: Double? = null,
    var totalSum: Double? = null
)