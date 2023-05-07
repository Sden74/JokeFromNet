package com.example.jokefromnet

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.annotation.StringRes
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import java.net.UnknownHostException

private lateinit var viewModel: ViewModel

class JokeApp : Application() {
    lateinit var viewModel: ViewModel
    override fun onCreate() {
        super.onCreate()
        /*val retrofit=Retrofit.Builder()
            .baseUrl("https://www.google.com")
            .addConverterFactory(GsonConverterFactory.create())
            .build()*/
        //viewModel = ViewModel(BaseModel(BaseJokeService(Gson()),BaseResourceManager(this)))
        viewModel = ViewModel(BaseModel(TestCacheDataSource(),TestCloudDataSource(),BaseResourceManager(this)))
        /*viewModel = ViewModel(
            BaseModel(
                retrofit.create(JokeService::class.java),
                BaseResourceManager(this)))*/
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewModel = (application as JokeApp).viewModel
        val button = findViewById<Button>(R.id.actionButton)
        val progressBar = findViewById<View>(R.id.progressBar)
        val textView = findViewById<TextView>(R.id.textView)
        val changeButton = findViewById<ImageView>(R.id.changeButton)
        val checkBox= findViewById<CheckBox>(R.id.checkBox)
        checkBox.setOnCheckedChangeListener{_,isChecked->
            viewModel.chooseFavorites(isChecked)
        }
        changeButton.setOnClickListener{
            viewModel.changeJokeStatus()
        }
        progressBar.visibility = View.INVISIBLE
        button.setOnClickListener {
            button.isEnabled = false
            progressBar.visibility = View.VISIBLE
            viewModel.getJoke()
        }

        viewModel.init(object : DataCallback {
            override fun provideText(text: String)=runOnUiThread() {
                button.isEnabled = true
                progressBar.visibility = View.INVISIBLE
                textView.text = text
            }

            override fun provideIconRes(id: Int) =runOnUiThread{changeButton.setImageResource(id)}
        })
    }
    override fun onDestroy() {
        viewModel.clear()
        super.onDestroy()
    }
}

//---------------------------------------------------------------------------------
interface DataCallback {
    fun provideText(text: String)
    fun provideIconRes(@DrawableRes id:Int)
}
interface JokeCallback {
    fun provide(joke: Joke)
}
class ViewModel(private val model: Model) {
    private var dataCallback: DataCallback? = null
    private val jokeCallback=object :JokeCallback{
        override fun provide(joke: Joke) {
            dataCallback?.let { joke.map(it) }
        }
    }
    fun init(callback: DataCallback) {
        dataCallback = callback
        model.init(jokeCallback)
    }

    fun getJoke() {
        model.getJoke()
    }

    fun clear() {
        dataCallback = null
        model.clear()
    }

    fun chooseFavorites(favorites: Boolean) {
        model.chooseDataSource(favorites)
    }

    fun changeJokeStatus() {
        model.changeJokeStatus(jokeCallback)
    }
}




//----------------------------JOKE_FAILURE-----------------------------------------
interface JokeFailure{
    fun getMessage():String
}
class NoConnection(private val resourceManager: ResourceManager):JokeFailure{
    override fun getMessage(): String = resourceManager.getString(R.string.no_connection)
}
class ServiceUnavailable(private val resourceManager: ResourceManager):JokeFailure{
    override fun getMessage(): String = resourceManager.getString(R.string.service_unavailable)
}
//-------------------------------------------------------------------------------------
interface ResourceManager{
    fun getString(@StringRes stringResId: Int):String
}
class BaseResourceManager(private val context: Context):ResourceManager{
    override fun getString(stringResId: Int): String = context.getString(stringResId)
}
interface JokeService{
    //fun getJoke(callback: ServiceCallback)
    @GET("https://official-joke-api.appspot.com/random_joke/")
    fun getJoke() : retrofit2.Call<JokeServerModel>
}


//-------------------------JOKE------------------------------------------
data class JokeServerModel(
    @SerializedName("id")
    private val id:Int,
    @SerializedName("type")
    private val type:String,
    @SerializedName("setup")
    private val text:String,
    @SerializedName("punchline")
    private val punchline:String
){
    fun toJoke()=BaseJoke(text,punchline)
    fun toBaseJoke()=BaseJoke(text,punchline)
    fun toFavoriteJoke()=FavoriteJoke(text,punchline)
    fun change(cacheDataSource: CacheDataSource) = cacheDataSource.addOrRemove(id,this)
}
abstract class Joke(private val text: String,private val punchline: String){
    fun getJokeUi()="$text\n$punchline"
    @DrawableRes abstract fun getIconResId():Int

    fun map(callback: DataCallback)=callback.run {
        provideText(getJokeUi())
        provideIconRes(getIconResId())
    }
}
class BaseJoke(text: String,punchline: String):Joke(text, punchline){
    override fun getIconResId() = R.drawable.baseline_favorite_border_24
}
class FavoriteJoke(text: String,punchline: String):Joke(text, punchline){
    override fun getIconResId() = R.drawable.baseline_favorite_24
}
class FailedJoke(text: String ):Joke(text, ""){
    override fun getIconResId() = 0
}

//----------------------------OLDS------------------------------------------------
interface ResultCallback {
    fun provideSuccess(data: Joke)
    fun provideError(data: JokeFailure)
}
interface UiMapper{
    fun toUiText():String
}
/*enum class ErrorType {
    NO_CONNECTION,
    OTHER
}*/
/*class BaseModel(
    private val service: JokeService,
    private val resourceManager: ResourceManager
):Model{
    private var callback:ResultCallback?=null
    private val noConnection by lazy { NoConnection(resourceManager) }
    private val serviceUnavailable by lazy { ServiceUnavailable(resourceManager) }
*//*
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
*//*
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
}*/
/*class TestModel(resourceManager: ResourceManager) : com.example.jokefromnet.Model {
    private var callback: JokeCallback? = null
    private var count = 0
    private val noConnection= NoConnection(resourceManager)
    private val serviceUnavailable=ServiceUnavailable(resourceManager)

    override fun getJoke() = Thread {
        Thread.sleep(1000)
        when (count){
            0 -> callback?.provide(BaseJoke("testText","testPunchline"))
            1 -> callback?.provide(FavoriteJoke("FavoriteJokeText","favorite joke punchline"))
            2 -> callback?.provide(FailedJoke(serviceUnavailable.getMessage()))
        }
        count++
        if (count==3) count=0
    }.start()

    override fun init(callback: JokeCallback) {
        this.callback = callback
    }

    override fun clear() {
        callback = null
    }
}*/
interface ServiceCallback{
    fun returnSuccess(data:JokeServerModel)
    fun returnError(type:ErrorType)
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
/*class Joke(private val text: String, private val punchline:String):UiMapper{
    override fun toUiText(): String = "$text\n$punchline"
    //fun getJokeUi()="$text\n$punchline"
}*/
class BaseCloudDataSource(private val service: JokeService):CloudDataSource{
    override fun getJoke(callback: JokeCloudCallback) {
        service.getJoke().enqueue(object : retrofit2.Callback<JokeServerModel>{
            override fun onResponse(
                call: Call<JokeServerModel>,
                response: Response<JokeServerModel>
            ) {
                if (response.isSuccessful){
                    callback.provide(response.body()!!)
                }else{
                    callback.fail(ErrorType.SERVICE_UNAVAILABLE)
                }
            }

            override fun onFailure(call: Call<JokeServerModel>, t: Throwable) {
                if (t is UnknownHostException){
                    callback.fail(ErrorType.NO_CONNECTION)
                }else{
                    callback.fail(ErrorType.SERVICE_UNAVAILABLE)
                }
            }
        })
    }
}

//--------------------------------DATASOURCES__________________________________________
interface CacheDataSource{
    fun getJoke(jokeCachedCallback: JokeCachedCallback)
    fun addOrRemove(id: Int,joke: JokeServerModel): Joke
}

interface CloudDataSource{
    fun getJoke(callback: JokeCloudCallback)
}


interface JokeCloudCallback{
    fun provide(joke: JokeServerModel)
    fun fail(error: ErrorType)

}
interface JokeCachedCallback{
    fun provide(jokeServerModel: JokeServerModel)
    fun fail()

}
enum class ErrorType{
    NO_CONNECTION,
    SERVICE_UNAVAILABLE
}

class TestCloudDataSource:CloudDataSource{
    private var count = 0
    override fun getJoke(callback: JokeCloudCallback) {
        val joke=JokeServerModel(count,"testType","TestText$count","TestPunchline$count")
        callback.provide(joke)
        count++
    }
}
class TestCacheDataSource : CacheDataSource{
    private val map=HashMap<Int,JokeServerModel>()
    override fun addOrRemove(id: Int, jokeServerModel: JokeServerModel): Joke {
        return if (map.containsKey(id)){
            val joke = map[id]!!.toBaseJoke()
            map.remove(id)
            joke
        } else {
            map[id] = jokeServerModel
            jokeServerModel.toFavoriteJoke()
        }
    }

    override fun getJoke(jokeCachedCallback: JokeCachedCallback) {
        if (map.isEmpty()){
            jokeCachedCallback.fail()
        }else{
            jokeCachedCallback.provide(map[0]!!)
        }
    }
}

class NoCachedJokes(private val resourceManager: ResourceManager):JokeFailure{
    override fun getMessage(): String {
        return resourceManager.getString(R.string.no_cached_jokes)
    }
}

//--------------------------------MODEL-------------------------------------------------
interface Model {
    fun getJoke()
    fun init(callback: JokeCallback)
    fun clear()
    fun changeJokeStatus(jokeCallback: JokeCallback)
    fun chooseDataSource(favorites: Boolean)
}
class BaseModel(
    private val cacheDataSource: CacheDataSource,
    private val cloudDataSource: CloudDataSource,
    private val resourceManager: ResourceManager
):Model{

    private val noConnection by lazy { NoConnection(resourceManager) }
    private val serviceUnavailable by lazy { ServiceUnavailable(resourceManager) }
    private var jokeCallback:JokeCallback?=null
    private var cachedJokeServerModel:JokeServerModel?=null
    private val noCachedJokes by lazy {  NoCachedJokes(resourceManager) }

    private var getJokeFromCache = false
    override fun getJoke() {
        if (getJokeFromCache){
            cacheDataSource.getJoke(object : JokeCachedCallback{
                override fun provide(jokeServerModel: JokeServerModel) {
                    jokeCallback?.provide(jokeServerModel.toFavoriteJoke())
                }

                override fun fail() {
                    jokeCallback?.provide(FailedJoke(noCachedJokes.getMessage()))
                }
            })
        }else {
            cloudDataSource.getJoke(object : JokeCloudCallback {
                override fun provide(joke: JokeServerModel) {
                    cachedJokeServerModel = joke
                    jokeCallback?.provide(joke.toBaseJoke())
                }

                override fun fail(error: ErrorType) {
                    cachedJokeServerModel = null
                    val failure =
                        if (error == ErrorType.NO_CONNECTION) noConnection else serviceUnavailable
                    jokeCallback?.provide(FailedJoke(failure.getMessage()))
                }
            })
        }
    }
    override fun changeJokeStatus(jokeCallback: JokeCallback) {
        cachedJokeServerModel?.change(cacheDataSource)?.let { jokeCallback.provide(it) }
    }
    override fun init(callback: JokeCallback) {
        jokeCallback=callback
    }
    override fun clear() {
        jokeCallback=null
    }

    override fun chooseDataSource(cached: Boolean) {
        getJokeFromCache=cached
    }
}
