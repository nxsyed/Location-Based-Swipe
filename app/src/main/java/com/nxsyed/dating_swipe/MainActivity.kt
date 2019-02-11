package com.nxsyed.dating_swipe

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.util.DiffUtil
import android.support.v7.widget.DefaultItemAnimator
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.JsonElement
import com.pubnub.api.PNConfiguration
import com.pubnub.api.PubNub
import com.pubnub.api.callbacks.PNCallback
import com.pubnub.api.callbacks.SubscribeCallback
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult
import com.yuyakaido.android.cardstackview.*
import java.util.*


class MainActivity : AppCompatActivity(), CardStackListener {

    private val cardStackView by lazy { findViewById<CardStackView>(R.id.card_stack_view) }
    private val manager by lazy { CardStackLayoutManager(this, this) }
    private val adapter by lazy { CardStackAdapter(createSpots("Welcome to Dating Swipe!", "0")) }
    private val MY_PERMISSIONS_REQUESTACCESS_COARSE_LOCATION = 1

    private lateinit var fusedLocationClient: FusedLocationProviderClient


    private val pnConfiguration = PNConfiguration()
    init {
        pnConfiguration.subscribeKey = "sub-c-87dbd99c-e470-11e8-8d80-3ee0fe19ec50"
        pnConfiguration.publishKey = "pub-c-09557b6c-9513-400f-a915-658c0789e264"
    }
    private val pubNub = PubNub(pnConfiguration)

    private val userLocation = mutableListOf<Double>(0.0,0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val androidID = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
            } else {
                ActivityCompat.requestPermissions(this,
                        arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION),
                        MY_PERMISSIONS_REQUESTACCESS_COARSE_LOCATION)
            }
        } else {
            fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            userLocation[0] = location.latitude
                            userLocation[1] = location.longitude
                            Log.d("Location", location.latitude.toString())
                        } else {
                            Log.d("Location", location?.latitude.toString())
                        }
                    }
        }


        var subscribeCallback: SubscribeCallback = object : SubscribeCallback() {
            override fun status(pubnub: PubNub, status: PNStatus) {

            }

            override fun message(pubnub: PubNub, message: PNMessageResult) {
                if(message.message.isJsonArray){
                    for (person: JsonElement in message.message.asJsonArray) {
                        pubNub.run {
                            publish()
                                    .message("""["$androidID", $person, ${userLocation[0]}, ${userLocation[1]}]""")
                                    .channel("distance")
                                    .async(object : PNCallback<PNPublishResult>() {
                                        override fun onResponse(result: PNPublishResult, status: PNStatus) {
                                            if (!status.isError) {
                                                println("Message was published")
                                            } else {
                                                println("Could not publish")
                                            }
                                        }
                                    })
                        }
                    }
                }else{
                    var person = message.message.asJsonObject
                    runOnUiThread { paginate(person.get("ID").toString(), person.get("distance").toString()) }
                }

            }

            override fun presence(pubnub: PubNub, presence: PNPresenceEventResult) {
            }
        }

        pubNub.run {
            addListener(subscribeCallback)
            subscribe()
                    .channels(Arrays.asList(androidID, "$androidID-distance"))
                    .execute()
        }

        setupCardStackView()
        setupButton()
    }

    override fun onStart() {
        super.onStart()
        val androidID = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID)

        pubNub.run {
            publish()
                    .message("""
                        {
                            "location": {
                                "lat":${userLocation[0]},
                                "long":${userLocation[1]}
                            },
                            "id": "$androidID"
                        }
                    """.trimIndent())
                    .channel("Users")
                    .async(object : PNCallback<PNPublishResult>() {
                        override fun onResponse(result: PNPublishResult, status: PNStatus) {
                            if (!status.isError) {
                                println("Message was published")
                            } else {
                                println("Could not publish")
                            }
                        }
                    })
        }

    }

    override fun onCardDragging(direction: Direction, ratio: Float) {
        Log.d("CardStackView", "onCardDragging: d = ${direction.name}, r = $ratio")
    }

    override fun onCardSwiped(direction: Direction) {
        Log.d("CardStackView", "onCardSwiped: p = ${manager.topPosition}, d = $direction")
        if (manager.topPosition == adapter.itemCount - 5) {
            paginate("", "")
        }
    }

    override fun onCardRewound() {
        Log.d("CardStackView", "onCardRewound: ${manager.topPosition}")
    }

    override fun onCardCanceled() {
        Log.d("CardStackView", "onCardCanceled: ${manager.topPosition}")
    }

    override fun onCardAppeared(view: View, position: Int) {
        val textView = view.findViewById<TextView>(R.id.item_name)
        Log.d("CardStackView", "onCardAppeared: ($position) ${textView.text}")
    }

    override fun onCardDisappeared(view: View, position: Int) {
        val textView = view.findViewById<TextView>(R.id.item_name)
        Log.d("CardStackView", "onCardDisappeared: ($position) ${textView.text}")
    }

    private fun setupCardStackView() {
        initialize()
    }

    private fun setupButton() {
        val skip = findViewById<View>(R.id.skip_button)
        skip.setOnClickListener {
            val setting = SwipeAnimationSetting.Builder()
                    .setDirection(Direction.Left)
                    .setDuration(200)
                    .setInterpolator(AccelerateInterpolator())
                    .build()
            manager.setSwipeAnimationSetting(setting)
            cardStackView.swipe()
        }

        val rewind = findViewById<View>(R.id.rewind_button)
        rewind.setOnClickListener {
            val setting = RewindAnimationSetting.Builder()
                    .setDirection(Direction.Bottom)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator())
                    .build()
            manager.setRewindAnimationSetting(setting)
            cardStackView.rewind()
        }

        val like = findViewById<View>(R.id.like_button)
        like.setOnClickListener {
            val setting = SwipeAnimationSetting.Builder()
                    .setDirection(Direction.Right)
                    .setDuration(200)
                    .setInterpolator(AccelerateInterpolator())
                    .build()
            manager.setSwipeAnimationSetting(setting)
            cardStackView.swipe()
        }
    }

    private fun initialize() {
        manager.setStackFrom(StackFrom.None)
        manager.setVisibleCount(3)
        manager.setTranslationInterval(8.0f)
        manager.setScaleInterval(0.95f)
        manager.setSwipeThreshold(0.3f)
        manager.setMaxDegree(20.0f)
        manager.setDirections(Direction.HORIZONTAL)
        manager.setCanScrollHorizontal(true)
        manager.setCanScrollVertical(true)
        cardStackView.layoutManager = manager
        cardStackView.adapter = adapter
        cardStackView.itemAnimator.apply {
            if (this is DefaultItemAnimator) {
                supportsChangeAnimations = false
            }
        }
    }

    private fun paginate(name: String?, distance: String?) {
        val old = adapter.getSpots()
        val new = old.plus(createSpots("Person: $name", "Distance: $distance"))
        val callback = SpotDiffCallback(old, new)
        val result = DiffUtil.calculateDiff(callback)
        adapter.setSpots(new)
        result.dispatchUpdatesTo(adapter)
    }

    private fun createSpots(personName: String, personDistance: String): List<Spot> {
        val spots = ArrayList<Spot>()
        spots.add(Spot(
                name = personName,
                distance = personDistance,
                url = "https://picsum.photos/200/300/?random"
        ))
        return spots
    }

}
