package io.github.iyaroslav.posprinter

import android.content.Intent
import android.content.SharedPreferences
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
        private const val BASE_URL = "http://grand.help-it.kz/"
        const val DEFAULT_URL = "${BASE_URL}api/orders/32/print/check/"

        private const val PRIVATE_MODE = 0
        private const val PREFERENCES_NAME = "url"
    }

    private val printer by lazy { PosPrinter60mm(this) }
    private val preview by lazy { findViewById<TicketPreview>(R.id.ticket) }
    private val stateView by lazy { findViewById<TextView>(R.id.tv_state) }

    private var ticket: Ticket? = null
    private var tickerNumber = 1

    private val cache: SharedPreferences by lazy { getSharedPreferences(PREFERENCES_NAME, PRIVATE_MODE) }

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

        inputText.setText(cache.getString(PREFERENCES_NAME, DEFAULT_URL))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        printer.onActivityResult(requestCode, resultCode, data)
    }

    fun handleButtonClick(view: View) {
        when (view.id) {
            R.id.btn_search -> if (printer.isConnected) {
                printer.disconnect()
            } else {
                btn_search.setText(R.string.action_connect)
            }
            R.id.btn_preview -> prepareRawTicket(save=true, is_preview=true)
            R.id.btn_print -> prepareRawTicket(save=false, is_print=true)
        }
    }

    private fun previewRawTicket() {
        try {
//            prepareRawTicket()

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
//            prepareRawTicket()

            if (ticket == null) {
                Toast.makeText(this, "Пожалуйста, подготовьте квитанцию сперва", Toast.LENGTH_SHORT).show()
                return
            }

            printer.send(ticket)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun prepareRawTicket(save: Boolean, is_preview: Boolean = false, is_print: Boolean = false) {
        if (save) {
            val editor = cache.edit()
            editor.putString(PREFERENCES_NAME, inputText?.text?.toString())
            editor.apply()
        }

        val url = inputText.text.toString()

        val manager = ApiManager()
        val service = manager.create(url)

        progressBar.visibility = View.VISIBLE

        service.getTicket().enqueue(object : Callback<io.github.iyaroslav.posprinter.Ticket> {
            override fun onResponse(
                call: Call<io.github.iyaroslav.posprinter.Ticket>,
                response: Response<io.github.iyaroslav.posprinter.Ticket>
            ) {
                var newRaw = "^V1" + "\n^BASIC" + "\n^CYRILLIC"

                val ticketData = response.body()
                val args = arrayListOf<String>()

                ticketData?.let {
                    if (ticketData.ticketNumber != null) {
                        args.add((tickerNumber++).toString() + "")
                        newRaw = newRaw.plus("\n^C:Счет №: ~")
                    }

                    if (ticketData.dateEnabled != null) {
                        args.add(DateFormat.format("dd.MM.yyyy, HH:mm", Date()).toString())
                        newRaw = newRaw.plus("\n^C:Дата: ~")
                    }

                    if (ticketData.garson != null) {
                        args.add(ticketData.garson ?: "")
                        newRaw = newRaw.plus("\n^C:Официант: ~")
                    }

                    if (ticketData.table != null) {
                        args.add(ticketData.table ?: "")
                        newRaw = newRaw.plus("\n^C:Стол №: ~")
                    }

                    ticketData.raw?.let { raw ->
                        val arguments = args.toTypedArray()

                        ticket = if (arguments.isNullOrEmpty()) {
                            newRaw = newRaw.plus("\n^HR" + "\n^M:Наименование - кол-во.|Сумма" + "\n^HR")

                            val formattedRaw = raw.replace("^", "\n^")

                            newRaw = newRaw.plus(formattedRaw)

                            newRaw = newRaw.plus("\n^HR\n^BR\n^BR\n^BR\n^BR\n^BR")

                            progressBar.visibility = View.GONE

                            TicketBuilder(printer)
                                .isCyrillic(true)
                                .raw(newRaw)
                                .build()
                        } else {
                            newRaw = newRaw.plus("\n^HR" + "\n^M:Наименование - кол-во.|Сумма" + "\n^HR")

                            val formattedRaw = raw.replace("^", "\n^")

                            newRaw = newRaw.plus(formattedRaw)

                            newRaw = newRaw.plus("\n^HR")

                            if (ticketData.sum != null) {
                                newRaw = newRaw.plus("\n^R:Сумма: ${ticketData.sum}")
                            }

                            if (ticketData.service != null) {
                                val serviceAmount = (ticketData.sum ?: 0L) * (ticketData.service ?: 0L) / 100
                                newRaw = newRaw.plus("\n^R:Обслуживание ${ticketData.service}%: $serviceAmount")
                            }

                            if (ticketData.totalSum != null) {
                                newRaw = newRaw.plus("\n^BR" + "\n^BR" + "\n^B&M&H&W:Итого:|${ticketData.totalSum}")
                                newRaw = newRaw.plus("\n^HR")
                            }

                            newRaw = newRaw.plus("\n^BR\n^BR\n^BR\n^BR\n^BR")

                            progressBar.visibility = View.GONE

                            TicketBuilder(printer)
                                .isCyrillic(true)
                                .raw(newRaw, *arguments)
                                .build()
                        }

                        if (is_preview) {
                            preview.setTicket(ticket)
                        } else if (is_print) {
                            printer.send(ticket)
                        }
                    }
                }
            }

            override fun onFailure(
                call: Call<io.github.iyaroslav.posprinter.Ticket>,
                t: Throwable
            ) {
                progressBar.visibility = View.GONE

                Toast.makeText(
                    this@MainActivity,
                    "Ошибка, не получилось загрузить данные",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun setState(value: String, color: Int) {
        stateView.text = "Статус: $value"
        stateView.setTextColor(ContextCompat.getColor(this, color))
    }

}