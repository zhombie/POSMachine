package io.github.iyaroslav.posprinter

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.leerybit.escpos.DeviceCallbacks
import com.leerybit.escpos.PosPrinter60mm
import com.leerybit.escpos.Ticket
import com.leerybit.escpos.TicketBuilder
import com.leerybit.escpos.bluetooth.BTService
import com.leerybit.escpos.widgets.TicketPreview
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val BASE_URL = "http://grand.help-it.kz/"
        const val DEFAULT_URL = "${BASE_URL}api/orders/32/print/check/"
    }

    private val manager: ApiManager by lazy { ApiManager() }
    private val service: TicketService by lazy { manager.create() }

    private val printer by lazy { PosPrinter60mm(this) }
    private val preview by lazy { findViewById<TicketPreview>(R.id.ticket) }
    private val stateView by lazy { findViewById<TextView>(R.id.tv_state) }

    private var ticket: Ticket? = null
    private var tickerNumber = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSearch = findViewById<Button>(R.id.btn_search)

        printer.setCharsetName("UTF-8")
        printer.setDeviceCallbacks(object : DeviceCallbacks {
            override fun onConnected() {
                btnSearch.setText(R.string.action_disconnect)
            }

            override fun onFailure() {
                Toast.makeText(this@MainActivity, "Соединение потеряно", Toast.LENGTH_SHORT).show()
            }

            override fun onDisconnected() {
                btnSearch.setText(R.string.action_connect)
            }
        })

        printer.setStateChangedListener { state, _ ->
            when (state) {
                BTService.STATE_NONE -> setState("Отсутствует", R.color.text)
                BTService.STATE_CONNECTED -> setState("Подключено", R.color.green)
                BTService.STATE_CONNECTING -> setState("Подключается", R.color.blue)
                BTService.STATE_LISTENING -> setState("Слушает", R.color.amber)
            }
        }

        inputText.setText(DEFAULT_URL)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        printer.onActivityResult(requestCode, resultCode, data)
    }

    fun handleButtonClick(view: View) {
        when (view.id) {
            R.id.btn_search -> if (printer.isConnected) printer.disconnect() else printer.connect()
            R.id.btn_preview -> previewRawTicket()
            R.id.btn_print -> printRawTicket()
        }
    }

    private fun previewRawTicket() {
        try {
            prepareRawTicket()

            if (ticket == null) {
                Toast.makeText(this, "Пожалуйста, подготовьте квитанцию сперва", Toast.LENGTH_SHORT).show()
                return
            }

            preview.setTicket(ticket)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun printRawTicket() {
        try {
            prepareRawTicket()

            if (ticket == null) {
                Toast.makeText(this, "Пожалуйста, подготовьте квитанцию сперва", Toast.LENGTH_SHORT).show()
                return
            }

            printer.send(ticket)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun prepareRawTicket() {
        var ticketData: io.github.iyaroslav.posprinter.Ticket? = null

        val call = service.getTicket(inputText.text.toString().drop(BASE_URL.length))
        call.enqueue(object : Callback<io.github.iyaroslav.posprinter.Ticket>{
            override fun onResponse(
                call: Call<io.github.iyaroslav.posprinter.Ticket>,
                response: Response<io.github.iyaroslav.posprinter.Ticket>
            ) {
                ticketData = response.body()
            }

            override fun onFailure(
                call: Call<io.github.iyaroslav.posprinter.Ticket>,
                t: Throwable
            ) {
                Toast.makeText(this@MainActivity, "Ошибка, не получилось загрузить данные", Toast.LENGTH_SHORT).show()
            }
        })

        if (ticketData?.raw.isNullOrBlank()) {
            ticket = TicketBuilder(printer)
                .isCyrillic(true)
                .raw(
                    this,
                    R.raw.ticket,
                    (tickerNumber++).toString() + "",
                    DateFormat.format("dd.MM.yyyy, HH:mm", Date()).toString(),
                    ticketData?.garson ?: "",
                    ticketData?.table ?: ""
                )
                .build()
        } else {
            ticket = TicketBuilder(printer)
                .isCyrillic(true)
                .raw(
                    ticketData?.raw,
                    (tickerNumber++).toString() + "",
                    DateFormat.format("dd.MM.yyyy, HH:mm", Date()).toString(),
                    ticketData?.garson ?: "",
                    ticketData?.table ?: ""
                )
                .build()
        }
    }

    private fun setState(value: String, color: Int) {
        stateView.text = "Статус: $value"
        stateView.setTextColor(ContextCompat.getColor(this, color))
    }

}