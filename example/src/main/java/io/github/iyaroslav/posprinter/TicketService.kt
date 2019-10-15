package io.github.iyaroslav.posprinter

import retrofit2.Call
import retrofit2.http.GET

interface TicketService {

    @GET("")
    fun getTicket(url: String): Call<Ticket>

}