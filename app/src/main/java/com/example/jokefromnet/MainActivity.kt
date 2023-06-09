package com.example.jokefromnet

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.annotation.StringRes
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.net.UnknownHostException

private lateinit var viewModel: ViewModel

class JokeApp : Application() {
    lateinit var viewModel: ViewModel
    override fun onCreate() {
        super.onCreate()
        val retrofit=Retrofit.Builder()
            .baseUrl("https://www.google.com")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        //viewModel = ViewModel(BaseModel(BaseJokeService(Gson()),BaseResourceManager(this)))
        viewModel = ViewModel(
            BaseModel(
                retrofit.create(JokeService::class.java),
                BaseResourceManager(this)))
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewModel = (application as JokeApp).viewModel
        val button = findViewById<Button>(R.id.action_batton)
        val progressBar = findViewById<View>(R.id.progressBar)
        val textView = findViewById<TextView>(R.id.textView)
        progressBar.visibility = View.INVISIBLE

        button.setOnClickListener {
            button.isEnabled = false
            progressBar.visibility = View.VISIBLE
            viewModel.getJoke()
        }

        viewModel.init(object : TextCallback {
            override fun provideText(text: String)=runOnUiThread() {
                button.isEnabled = true
                progressBar.visibility = View.INVISIBLE
                textView.text = text
            }
        })
    }

    override fun onDestroy() {
        viewModel.clear()
        super.onDestroy()
    }
}


interface TextCallback {
    fun provideText(text: String)
}

class ViewModel(private val model: com.example.jokefromnet.Model) {
    private var callback: TextCallback? = null

    fun init(callback: TextCallback) {
        this.callback = callback
        model.init(object : ResultCallback {
            override fun provideSuccess(data: Joke) = callback.provideText(data.toUiText())
            override fun provideError(error: JokeFailure) = callback.provideText(error.getMessage())
        })
    }

    fun getJoke() {
        model.getJoke()
    }

    fun clear() {
        callback = null
        model.clear()
    }
}

interface Model {
    fun getJoke()
    fun init(callback: ResultCallback)
    fun clear()
}

interface ResultCallback {
    fun provideSuccess(data: Joke)
    fun provideError(data: JokeFailure)
}

class TestModel(resourceManager: ResourceManager) : com.example.jokefromnet.Model {
    private var callback: ResultCallback? = null
    private var count = 0
    private val noConnection= NoConnection(resourceManager)
    private val serviceUnavailable=ServiceUnavailable(resourceManager)

    override fun getJoke() = Thread {
        Thread.sleep(1000)
        when (count){
            0 -> callback?.provideSuccess(Joke("testText","testPunchline"))
            1 -> callback?.provideError(noConnection)
            2 -> callback?.provideError(serviceUnavailable)
        }
        count++
        if (count==3) count=0
    }.start()

    override fun init(callback: ResultCallback) {
        this.callback = callback
    }

    override fun clear() {
        callback = null
    }
}
interface UiMapper{
    fun toUiText():String
}
class Joke(private val text: String, private val punchline:String):UiMapper{
    override fun toUiText(): String = "$text\n$punchline"
    //fun getJokeUi()="$text\n$punchline"
}

interface JokeFailure{
    fun getMessage():String
}

class NoConnection(private val resourceManager: ResourceManager):JokeFailure{
    override fun getMessage(): String = resourceManager.getString(R.string.no_connection)
}

class ServiceUnavailable(private val resourceManager: ResourceManager):JokeFailure{
    override fun getMessage(): String = resourceManager.getString(R.string.service_unavailable)
}

interface ResourceManager{
    fun getString(@StringRes stringResId: Int):String
}

class BaseResourceManager(private val context: Context):ResourceManager{
    override fun getString(stringResId: Int): String = context.getString(stringResId)
}

interface JokeService{
    //fun getJoke(callback: ServiceCallback)
    @GET("https://official-joke-api.appspot.com/random_joke/")
    fun getJoke() : retrofit2.Call<JokeDTO>
}
interface ServiceCallback{
    fun returnSuccess(data:JokeDTO)
    fun returnError(type:ErrorType)
}

enum class ErrorType {
    NO_CONNECTION,
    OTHER
}

/*
class BaseJokeService(private val gson: Gson):JokeService{
    override fun getJoke(callback: ServiceCallback) {
        Thread{
            var connection:HttpURLConnection?=null
            try {
                val url= URL(JOKE_URL)
                connection=url.openConnection() as HttpURLConnection
                InputStreamReader(BufferedInputStream(connection.inputStream)).use {
                    val line: String=it.readText()
                    val dto=gson.fromJson(line,JokeDTO::class.java)
                    callback.returnSuccess(dto)
                }
            }catch (e:Exception){
                if (e is UnknownHostException)
                    callback.returnError(ErrorType.NO_CONNECTION)
                else
                    callback.returnError(ErrorType.OTHER)
            }finally {
                connection?.disconnect()
            }
        }.start()
    }
    private companion object{
        const val JOKE_URL="https://official-joke-api.appspot.com/random_joke/"
    }
}
*/

class BaseModel(
    private val service: JokeService,
    private val resourceManager: ResourceManager
):Model{
    private var callback:ResultCallback?=null
    private val noConnection by lazy { NoConnection(resourceManager) }
    private val serviceUnavailable by lazy { ServiceUnavailable(resourceManager) }
/*
    override fun getJoke() {
        service.getJoke(object :ServiceCallback{
            override fun returnSuccess(data: JokeDTO) {
                callback?.provideSuccess(data.toJoke())
            }

            override fun returnError(type: ErrorType) {
                when(type){
                    ErrorType.NO_CONNECTION -> callback?.provideError(noConnection)
                    ErrorType.OTHER -> callback?.provideError(serviceUnavailable)
                }
            }
        })
    }
*/
    override fun getJoke() {
        service.getJoke().enqueue(object : retrofit2.Callback<JokeDTO>{
            override fun onResponse(call: Call<JokeDTO>, response: Response<JokeDTO>) {
                if (response.isSuccessful){
                    callback?.provideSuccess(response.body()!!.toJoke())
                }else{
                    callback?.provideError(serviceUnavailable)
                }
            }

            override fun onFailure(call: Call<JokeDTO>, t: Throwable) {
                if (t is UnknownHostException){
                    callback?.provideError(noConnection)
                }else{
                    callback?.provideError(serviceUnavailable)
                }
            }
        })
    }
    override fun init(callback: ResultCallback) {
        this.callback=callback
    }

    override fun clear() {
        callback=null
    }
}

data class JokeDTO(
    @SerializedName("id")
    private val id:Int,
    @SerializedName("type")
    private val type:String,
    @SerializedName("setup")
    private val text:String,
    @SerializedName("punchline")
    private val punchline:String
){
    fun toJoke()=Joke(text,punchline)
}