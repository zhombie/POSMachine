package io.github.iyaroslav.posprinter

import io.github.iyaroslav.posprinter.MainActivity.Companion.BASE_URL
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiManager {

    fun create(): TicketService {
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create<TicketService>(TicketService::class.java)
    }

}