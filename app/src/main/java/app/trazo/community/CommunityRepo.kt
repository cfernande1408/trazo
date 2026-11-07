package app.trazo.community

import android.content.Context
import app.trazo.gpx.community.Community
import app.trazo.gpx.community.Hazard
import app.trazo.gpx.community.HazardKind
import app.trazo.gpx.community.Review
import app.trazo.gpx.osm.Bbox
import java.time.Instant

/** Lo que la app pide y manda a la comunidad. */
object CommunityRepo {

    suspend fun reviews(context: Context, routeKey: String): List<Review> =
        Community.parseReviews(Supabase.request(context, "GET", Community.reviewsPath(routeKey)))

    suspend fun addReview(context: Context, routeKey: String, rating: Int, comment: String?, author: String?, activity: String) {
        Supabase.request(context, "POST", "/rest/v1/route_reviews", Community.reviewBody(routeKey, rating, comment, author, activity))
    }

    suspend fun hazards(context: Context, area: Bbox): List<Hazard> =
        Community.parseHazards(Supabase.request(context, "GET", Community.hazardsPath(area, Instant.now().toString())))

    suspend fun report(context: Context, kind: HazardKind, lat: Double, lon: Double, note: String?) {
        val expires = Instant.ofEpochMilli(Community.expiresAt(kind, System.currentTimeMillis())).toString()
        Supabase.request(context, "POST", "/rest/v1/hazards", Community.hazardBody(kind, lat, lon, note, expires))
    }

    suspend fun vote(context: Context, hazard: Hazard, stillThere: Boolean) {
        Supabase.request(context, "POST", "/rest/v1/rpc/vote_hazard", """{"h":${hazard.id},"still":$stillThere}""")
    }
}
