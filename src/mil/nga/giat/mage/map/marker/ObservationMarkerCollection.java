package mil.nga.giat.mage.map.marker;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import mil.nga.giat.mage.observation.ObservationViewActivity;
import mil.nga.giat.mage.sdk.datastore.observation.Observation;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.MarkerManager;
import com.vividsolutions.jts.geom.Point;

public class ObservationMarkerCollection implements PointCollection<Observation>, OnMarkerClickListener {

    private GoogleMap map;
    private Context context;
    private Date latestObservationDate = new Date(0);

    private boolean visible = true;

    private Map<Long, Marker> observationIdToMarker = new ConcurrentHashMap<Long, Marker>();
    private Map<String, Observation> markerIdToObservation = new ConcurrentHashMap<String, Observation>();

    private MarkerManager.Collection markerCollection;

    public ObservationMarkerCollection(Context context, GoogleMap map) {
        this.map = map;
        this.context = context;

        MarkerManager markerManager = new MarkerManager(map);
        markerCollection = markerManager.newCollection();
    }

    @Override
    public void add(Observation o) {
        // If I got an observation that I already have in my list
        // remove it from the map and clean-up my collections
        Marker marker = observationIdToMarker.remove(o.getId());
        if (marker != null) {
            markerIdToObservation.remove(marker.getId());
            marker.remove();
        }

        Point point = (Point) o.getObservationGeometry().getGeometry();
        MarkerOptions options = new MarkerOptions()
            .position(new LatLng(point.getY(), point.getX()))
            .icon(ObservationBitmapFactory.bitmapDescriptor(context, o))
            .visible(visible);

        marker = markerCollection.addMarker(options);
        observationIdToMarker.put(o.getId(), marker);
        markerIdToObservation.put(marker.getId(), o);
        
        if (o.getLastModified().after(latestObservationDate)) {
            latestObservationDate = o.getLastModified();
        }
    }

    @Override
    public void addAll(Collection<Observation> observations) {
        for (Observation o : observations) {
            add(o);
        }
    }

    @Override
    public void setVisibility(boolean visible) {
        if (this.visible == visible)
            return;
        
        this.visible = visible;
        for (Marker m : observationIdToMarker.values()) {
            m.setVisible(visible);
        }
    }

    @Override
    public void remove(Observation o) {
        Marker marker = observationIdToMarker.remove(o.getId());
        if (marker != null) {
            markerIdToObservation.remove(marker.getId());
            markerCollection.remove(marker);
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        Observation o = markerIdToObservation.get(marker.getId());
        
        if (o == null) return false;  // Not an observation let someone else handle it

        Intent intent = new Intent(context, ObservationViewActivity.class);
        intent.putExtra(ObservationViewActivity.OBSERVATION_ID, o.getId());
        intent.putExtra(ObservationViewActivity.INITIAL_LOCATION, map.getCameraPosition().target);
        intent.putExtra(ObservationViewActivity.INITIAL_ZOOM, map.getCameraPosition().zoom);
        context.startActivity(intent);

        return true;
    }
    
	@Override
	public void refreshMarkerIcons() {
		for (Marker m : markerCollection.getMarkers()) {
			Observation to = markerIdToObservation.get(m.getId());
			if (to != null) {
				m.setIcon(ObservationBitmapFactory.bitmapDescriptor(context, markerIdToObservation.get(m.getId())));
			}
		}
	}

    @Override
    public void clear() {
        observationIdToMarker.clear();
        markerIdToObservation.clear();
        markerCollection.clear();
        latestObservationDate = new Date(0);
    }

    @Override
    public Date getLatestDate() {
        return latestObservationDate;
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        // do nothing I don't care
    }
}