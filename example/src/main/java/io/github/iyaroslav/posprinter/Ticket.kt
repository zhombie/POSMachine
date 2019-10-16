package io.github.iyaroslav.posprinter

import com.google.gson.annotations.SerializedName

data class Ticket(
    @SerializedName("ticket_number")
    var ticketNumber: Int? = null,
    @SerializedName("date_enabled")
    var dateEnabled: Boolean? = null,
    var raw: String? = null,
    var garson: String? = null,
    var table: String? = null,
    var sum: Long? = null,
    var service: Long? = null,
    @SerializedName("total_sum")
    var totalSum: Long? = null
)