/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */

package com.bubelov.coins.feature.notificationarea

import android.Manifest
import android.arch.lifecycle.ViewModelProvider
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.navigation.fragment.findNavController
import com.bubelov.coins.BuildConfig
import com.bubelov.coins.R
import com.bubelov.coins.model.NotificationArea
import com.bubelov.coins.util.OnSeekBarChangeAdapter
import com.bubelov.coins.util.viewModelProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_notification_area.*
import javax.inject.Inject

class NotificationAreaFragment : DaggerFragment(), OnMapReadyCallback {
    @Inject lateinit var modelFactory: ViewModelProvider.Factory
    private val model by lazy { viewModelProvider(modelFactory) as NotificationAreaViewModel }

    private var map: GoogleMap? = null

    private var marker: Marker? = null
    private var areaCircle: Circle? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notification_area, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.setNavigationOnClickListener {
            saveArea()
            findNavController().popBackStack()
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map_fragment) as MapFragment
        mapFragment.getMapAsync(this)

        seek_bar_radius.progressDrawable.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.accent),
            PorterDuff.Mode.SRC_IN
        )

        seek_bar_radius.thumb.setColorFilter(ContextCompat.getColor(requireContext(), R.color.accent), PorterDuff.Mode.SRC_IN)

        Handler().post {
            bottom_panel.post { map!!.setPadding(0, 0, 0, bottom_panel.height) }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map!!.uiSettings.isMyLocationButtonEnabled = false
        map!!.uiSettings.isZoomControlsEnabled = false
        map!!.uiSettings.isCompassEnabled = false
        map!!.setOnMarkerDragListener(OnMarkerDragListener())

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map!!.isMyLocationEnabled = true
        }

        // TODO
        //showArea(model.getNotificationArea(defaultCameraPosition))
    }

    private fun showArea(area: NotificationArea) {
        marker?.remove()
        areaCircle?.remove()

        val markerDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.ic_map_marker_location)

        marker = map!!.addMarker(
            MarkerOptions()
                .position(LatLng(area.latitude, area.longitude))
                .icon(markerDescriptor)
                .anchor(BuildConfig.MAP_MARKER_ANCHOR_U, BuildConfig.MAP_MARKER_ANCHOR_V)
                .draggable(true)
        )

        val circleOptions = CircleOptions()
            .center(marker?.position)
            .radius(area.radius)
            .fillColor(ContextCompat.getColor(requireContext(), R.color.notification_area))
            .strokeColor(ContextCompat.getColor(requireContext(), R.color.notification_area_border))
            .strokeWidth(4f)

        areaCircle = map!!.addCircle(circleOptions)

        seek_bar_radius.max = 500000
        seek_bar_radius.progress = areaCircle!!.radius.toInt()
        seek_bar_radius.setOnSeekBarChangeListener(SeekBarChangeListener())

        val areaCenter = LatLng(area.latitude, area.longitude)
        map!!.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                areaCenter,
                (model.getZoomLevel(areaCircle!!) - 1).toFloat()
            )
        )
    }

    private fun saveArea() {
        val area = NotificationArea(
            areaCircle!!.center.latitude,
            areaCircle!!.center.longitude,
            areaCircle!!.radius
        )

        model.save(area)
    }

    private inner class OnMarkerDragListener : GoogleMap.OnMarkerDragListener {
        override fun onMarkerDragStart(marker: Marker) {
            areaCircle!!.fillColor = ContextCompat.getColor(requireContext(), android.R.color.transparent)
        }

        override fun onMarkerDrag(marker: Marker) {
            areaCircle!!.center = marker.position
            map!!.animateCamera(CameraUpdateFactory.newLatLng(marker.position))
        }

        override fun onMarkerDragEnd(marker: Marker) {
            areaCircle!!.fillColor = ContextCompat.getColor(requireContext(), R.color.notification_area)
            saveArea()
        }
    }

    private inner class SeekBarChangeListener : OnSeekBarChangeAdapter() {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            areaCircle!!.radius = progress.toDouble()
        }
    }
}