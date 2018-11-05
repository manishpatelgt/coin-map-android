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

package com.bubelov.coins.repository.place

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import com.bubelov.coins.model.Currency
import com.bubelov.coins.model.Place
import com.bubelov.coins.util.toLatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.util.*

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesRepository @Inject constructor(
    private val api: PlacesApi,
    private val db: PlacesDb,
    private val assetsCache: PlacesAssetsCache
) {
    private val allPlaces = db.allAsync()

    init {
        db.count().observeForever { count ->
            if (count == 0) {
                db.insert(assetsCache.getPlaces())
            }
        }
    }

    suspend fun find(id: Long): Place? {
        return withContext(Dispatchers.IO) {
            db.find(id)
        }
    }

    suspend fun findBySearchQuery(searchQuery: String): List<Place> {
        return withContext(Dispatchers.IO) {
            db.findBySearchQuery(searchQuery)
        }
    }

    fun countByCurrency(currency: Currency) = db.countByCurrencyCode(currency.code)

    fun findRandom() = db.findRandom()

    fun getPlaces(bounds: LatLngBounds): LiveData<List<Place>> =
        Transformations.switchMap(allPlaces) {
            MutableLiveData<List<Place>>().apply {
                value = it.filter { bounds.contains(it.toLatLng()) }
            }
        }

    suspend fun fetchNewPlaces(): List<Place> {
        return withContext(Dispatchers.IO) {
            val latestPlaceUpdatedAt = db.maxUpdatedAt() ?: Date(0)
            val response = api.getPlaces(Date(latestPlaceUpdatedAt.time + 1)).await()
            db.insert(response)
            response
        }
    }

    suspend fun addPlace(place: Place): Place {
        return withContext(Dispatchers.IO) {
            val result = api.addPlace(place).await()
            db.insert(listOf(result))
            result
        }
    }

    suspend fun updatePlace(place: Place): Place {
        return withContext(Dispatchers.IO) {
            val result = api.updatePlace(place).await()
            db.update(result)
            result
        }
    }
}