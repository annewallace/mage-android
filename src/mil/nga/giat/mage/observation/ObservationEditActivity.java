package mil.nga.giat.mage.observation;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import mil.nga.giat.mage.R;
import mil.nga.giat.mage.form.LayoutBaker;
import mil.nga.giat.mage.form.MageSpinner;
import mil.nga.giat.mage.map.marker.ObservationBitmapFactory;
import mil.nga.giat.mage.sdk.datastore.common.State;
import mil.nga.giat.mage.sdk.datastore.observation.Attachment;
import mil.nga.giat.mage.sdk.datastore.observation.Observation;
import mil.nga.giat.mage.sdk.datastore.observation.ObservationGeometry;
import mil.nga.giat.mage.sdk.datastore.observation.ObservationHelper;
import mil.nga.giat.mage.sdk.datastore.observation.ObservationProperty;
import mil.nga.giat.mage.sdk.datastore.user.User;
import mil.nga.giat.mage.sdk.datastore.user.UserHelper;
import mil.nga.giat.mage.sdk.exceptions.ObservationException;
import mil.nga.giat.mage.sdk.exceptions.UserException;
import mil.nga.giat.mage.sdk.utils.DateUtility;
import mil.nga.giat.mage.sdk.utils.MediaUtility;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import com.bumptech.glide.Glide;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

public class ObservationEditActivity extends Activity {

	private static final String LOG_NAME = ObservationEditActivity.class.getName();

	public static final String OBSERVATION_ID = "OBSERVATION_ID";
	public static final String LOCATION = "LOCATION";
	public static final String INITIAL_LOCATION = "INITIAL_LOCATION";
	public static final String INITIAL_ZOOM = "INITIAL_ZOOM";

	private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
	private static final int CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE = 200;
	private static final int CAPTURE_VOICE_ACTIVITY_REQUEST_CODE = 300;
	private static final int GALLERY_ACTIVITY_REQUEST_CODE = 400;
	private static final int ATTACHMENT_VIEW_ACTIVITY_REQUEST_CODE = 500;
	private static final int LOCATION_EDIT_ACTIVITY_REQUEST_CODE = 600;

	private static final long NEW_OBSERVATION = -1L;

	private Date date;
	private DecimalFormat latLngFormat = new DecimalFormat("###.#####");
	private ArrayList<Attachment> attachments = new ArrayList<Attachment>();
	private Location l;
	private Observation o;
	private GoogleMap map;
	private Marker observationMarker;
	private Circle accuracyCircle;
	private long locationElapsedTimeMilliseconds = 0;

	private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm zz", Locale.getDefault());

	// View fields
	private MageSpinner typeSpinner;
	private MageSpinner levelSpinner;

	private static int typeSpinnerLastPosition = 0;
	private static int levelSpinnerLastPosition = 0;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.observation_editor);

		Intent intent = getIntent();
		final long observationId = intent.getLongExtra(OBSERVATION_ID, NEW_OBSERVATION);

		typeSpinner = (MageSpinner) findViewById(R.id.type_spinner);
		typeSpinner.setSelection(typeSpinnerLastPosition);
		typeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (observationId == NEW_OBSERVATION) {
					typeSpinnerLastPosition = position;
				}
				onTypeOrLevelChanged("type", parent.getItemAtPosition(position).toString());
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		levelSpinner = (MageSpinner) findViewById(R.id.level_spinner);
		levelSpinner.setSelection(levelSpinnerLastPosition);
		levelSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (observationId == NEW_OBSERVATION) {
					levelSpinnerLastPosition = position;
				}
				onTypeOrLevelChanged("EVENTLEVEL", parent.getItemAtPosition(position).toString());
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		hideKeyboardOnClick(findViewById(R.id.observation_edit));

		if (observationId == NEW_OBSERVATION) {
			this.setTitle("Create New Observation");
			l = intent.getParcelableExtra(LOCATION);
			date = new Date();
			((TextView) findViewById(R.id.date)).setText(sdf.format(date));

			// set default type and level values for map marker
			o = new Observation();
			o.getProperties().add(new ObservationProperty("type", typeSpinner.getSelectedItem().toString()));
			o.getProperties().add(new ObservationProperty("EVENTLEVEL", levelSpinner.getSelectedItem().toString()));
			try {
				User u = UserHelper.getInstance(getApplicationContext()).readCurrentUser();
				if (u != null) {
					o.setUserId(u.getRemoteId());
				}
			} catch (UserException ue) {
				ue.printStackTrace();
			}
		} else {
			this.setTitle("Edit Observation");
			// this is an edit of an existing observation
			try {
				o = ObservationHelper.getInstance(getApplicationContext()).read(getIntent().getLongExtra(OBSERVATION_ID, 0L));
				attachments.addAll(o.getAttachments());
				for (Attachment a : attachments) {
					addAttachmentToGallery(a);
				}

				Map<String, ObservationProperty> propertiesMap = o.getPropertiesMap();
				String dateText = propertiesMap.get("timestamp").getValue();
				try {
					date = DateUtility.getISO8601().parse(dateText);
					dateText = sdf.format(date);
				} catch (ParseException e) {
					e.printStackTrace();
				}

				((TextView) findViewById(R.id.date)).setText(dateText);
				Geometry geo = o.getObservationGeometry().getGeometry();
				if (geo instanceof Point) {
					Point point = (Point) geo;
					String provider = "manual";
					if (propertiesMap.get("provider") != null) {
						provider = propertiesMap.get("provider").getValue();
					}
					l = new Location(provider);
					if (propertiesMap.containsKey("accuracy")) {
						l.setAccuracy(Float.parseFloat(propertiesMap.get("accuracy").getValue()));
					}
					l.setLatitude(point.getY());
					l.setLongitude(point.getX());
				}
				LayoutBaker.populateLayoutFromMap((LinearLayout) findViewById(R.id.form), propertiesMap);
			} catch (ObservationException oe) {

			}
		}

		findViewById(R.id.date_edit).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(ObservationEditActivity.this);
				// Get the layout inflater
				LayoutInflater inflater = getLayoutInflater();
				View dialogView = inflater.inflate(R.layout.date_time_dialog, null);
				final DatePicker datePicker = (DatePicker) dialogView.findViewById(R.id.date_picker);
				final TimePicker timePicker = (TimePicker) dialogView.findViewById(R.id.time_picker);
				// Inflate and set the layout for the dialog
				// Pass null as the parent view because its going in the dialog layout
				builder.setView(dialogView).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						Calendar c = Calendar.getInstance();
						c.set(datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth(), timePicker.getCurrentHour(), timePicker.getCurrentMinute(), 0);
						date = c.getTime();
						((TextView) findViewById(R.id.date)).setText(date.toString());
					}
				}).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
				AlertDialog ad = builder.create();
				ad.show();
			}
		});

		findViewById(R.id.location_edit).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(ObservationEditActivity.this, LocationEditActivity.class);
				intent.putExtra(LocationEditActivity.LOCATION, l);
				intent.putExtra(LocationEditActivity.MARKER_BITMAP, ObservationBitmapFactory.bitmap(ObservationEditActivity.this, o));
				startActivityForResult(intent, LOCATION_EDIT_ACTIVITY_REQUEST_CODE);
			}
		});

		setupMap();
	}
	
	/**
	 * Hides keyboard when clicking elsewhere
	 * 
	 * @param view
	 */
	private void hideKeyboardOnClick(View view) {
		// Set up touch listener for non-text box views to hide keyboard.
		if (!(view instanceof EditText) && !(view instanceof Button)) {
			view.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
					if (getCurrentFocus() != null) {
						inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
					}
					return false;
				}
			});
		}

		// If a layout container, iterate over children and seed recursion.
		if (view instanceof ViewGroup) {
			for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
				View innerView = ((ViewGroup) view).getChildAt(i);
				hideKeyboardOnClick(innerView);
			}
		}
	}
	
	@SuppressLint("NewApi")
	private long getElapsedTimeInMilliseconds() {
		long elapsedTimeInMilliseconds = 0;
		if (Build.VERSION.SDK_INT >= 17 && l.getElapsedRealtimeNanos() != 0) {
			elapsedTimeInMilliseconds = ((SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()) / (1000000l));
		} else {
			elapsedTimeInMilliseconds = System.currentTimeMillis() - l.getTime();
		}
		return Math.max(0l, elapsedTimeInMilliseconds);
	}

	private String elapsedTime(long ms) {
		String s = "";
		long sec = ms / 1000;
		long min = sec / 60;
		if (min == 0) {
			s = sec + ((sec == 1) ? " sec ago" : " secs ago");
		} else if (min < 60) {
			s = min + ((min == 1) ? " min ago" : " mins ago");
		} else {
			long hour = Math.round(Math.floor(min / 60));
			s = hour + ((hour == 1) ? " hour ago" : " hours ago");
		}
		return s;
	}

	private void setupMap() {
		map = ((MapFragment) getFragmentManager().findFragmentById(R.id.background_map)).getMap();

		LatLng location = new LatLng(l.getLatitude(), l.getLongitude());
		((TextView) findViewById(R.id.location)).setText(latLngFormat.format(l.getLatitude()) + ", " + latLngFormat.format(l.getLongitude()));
		if (l.getProvider() != null) {
			((TextView)findViewById(R.id.location_provider)).setText("("+l.getProvider()+")");
		} else {
			findViewById(R.id.location_provider).setVisibility(View.GONE);
		}
		if (l.getAccuracy() != 0) {
			((TextView)findViewById(R.id.location_accuracy)).setText("\u00B1" + l.getAccuracy() + "m");
		} else {
			findViewById(R.id.location_accuracy).setVisibility(View.GONE);
		}
		
		locationElapsedTimeMilliseconds = getElapsedTimeInMilliseconds();
		if (locationElapsedTimeMilliseconds != 0) {
			//String dateText = DateUtils.getRelativeTimeSpanString(System.currentTimeMillis() - locationElapsedTimeMilliseconds, System.currentTimeMillis(), 0).toString();
			String dateText = elapsedTime(locationElapsedTimeMilliseconds);
			((TextView)findViewById(R.id.location_elapsed_time)).setText(dateText);
		} else {
			findViewById(R.id.location_elapsed_time).setVisibility(View.GONE);
		}
		
		
        LatLng latLng = getIntent().getParcelableExtra(INITIAL_LOCATION);
        if (latLng == null) {
            latLng = new LatLng(0,0);
        }
        
        float zoom = getIntent().getFloatExtra(INITIAL_ZOOM, 0);
        
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
		
		map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 18));
		
		if (accuracyCircle != null) {
			accuracyCircle.remove();
		}
		
		CircleOptions circleOptions = new CircleOptions()
		.fillColor(getResources().getColor(R.color.accuracy_circle_fill))
		.strokeColor(getResources().getColor(R.color.accuracy_circle_stroke))
		.strokeWidth(5)
	    .center(location)
	    .radius(l.getAccuracy());
		accuracyCircle = map.addCircle(circleOptions);

		
		if (observationMarker != null) {
			observationMarker.setPosition(location);
			observationMarker.setIcon(ObservationBitmapFactory.bitmapDescriptor(this, o));
		} else {
			observationMarker = map.addMarker(new MarkerOptions().position(location).icon(ObservationBitmapFactory.bitmapDescriptor(this, o)));
		}
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		// Always call the superclass so it can restore the view hierarchy
		super.onRestoreInstanceState(savedInstanceState);

		l = savedInstanceState.getParcelable("location");
		attachments = savedInstanceState.getParcelableArrayList("attachments");

		for (Attachment a : attachments) {
			addAttachmentToGallery(a);
		}

		LinearLayout form = (LinearLayout) findViewById(R.id.form);
		LayoutBaker.populateLayoutFromBundle(form, savedInstanceState);
		currentImageUri = savedInstanceState.getParcelable("currentImageUri");
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		LayoutBaker.populateBundleFromLayout((LinearLayout) findViewById(R.id.form), outState);
		outState.putParcelable("location", l);
		outState.putParcelableArrayList("attachments", new ArrayList<Attachment>(attachments));
		outState.putParcelable("currentImageUri", currentImageUri);
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.observation_edit_menu, menu);
		return true;
	}
	

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.observation_save:
			o.setState(State.ACTIVE);
			o.setDirty(true);
			o.setObservationGeometry(new ObservationGeometry(new GeometryFactory().createPoint(new Coordinate(l.getLongitude(), l.getLatitude()))));

			Map<String, ObservationProperty> propertyMap = LayoutBaker.populateMapFromLayout((LinearLayout) findViewById(R.id.form));
			
			// Add properties that weren't part of the layout
			propertyMap.put("accuracy", new ObservationProperty("accuracy", Float.toString(l.getAccuracy())));
			String provider = l.getProvider();
			if (provider == null || provider.trim().isEmpty()) {
				provider = "manual";
			}
			propertyMap.put("provider", new ObservationProperty("provider", provider));
			propertyMap.put("delta", new ObservationProperty("delta", Long.toString(locationElapsedTimeMilliseconds)));

			o.addProperties(propertyMap.values());

			o.setAttachments(attachments);

			ObservationHelper oh = ObservationHelper.getInstance(getApplicationContext());
			try {
				if (o.getId() == null) {
					Observation newObs = oh.create(o);
					Log.i(LOG_NAME, "Created new observation with id: " + newObs.getId());
				} else {
					oh.update(o);
					Log.i(LOG_NAME, "Updated observation with remote id: " + o.getRemoteId());
				}
				finish();
			} catch (Exception e) {
				Log.e(LOG_NAME, e.getMessage(), e);
			}

			break;
		case R.id.observation_cancel:
			new AlertDialog.Builder(this).setTitle("Discard Changes").setMessage(R.string.cancel_edit).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			}).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
				}
			}).show();
			break;
		}

		return super.onOptionsItemSelected(item);
	}

	public void cameraButtonPressed(View v) {
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		File f = null;
        try {
        	f = MediaUtility.createImageFile();
        } catch (IOException ex) {
            // Error occurred while creating the File
        	ex.printStackTrace();
        }
        // Continue only if the File was successfully created
        if (f != null) {
        	currentImageUri = Uri.fromFile(f);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentImageUri);
            startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
        }
	}

	public void videoButtonPressed(View v) {
		Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
		startActivityForResult(intent, CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
	}

	public void voiceButtonPressed(View v) {
		Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
		startActivityForResult(intent, CAPTURE_VOICE_ACTIVITY_REQUEST_CODE);
	}

	public void fromGalleryButtonPressed(View v) {
		Intent intent = new Intent();
		intent.setType("image/*, video/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		Log.i(LOG_NAME, "build version sdk int: " + Build.VERSION.SDK_INT);
		if (Build.VERSION.SDK_INT >= 18) {
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		}
		startActivityForResult(intent, GALLERY_ACTIVITY_REQUEST_CODE);
	}

	private void addAttachmentToGallery(final Attachment a) {
		LinearLayout l = (LinearLayout) findViewById(R.id.image_gallery);
		
		final String absPath = a.getLocalPath();
		final String remoteId = a.getRemoteId();
		ImageView iv = new ImageView(getApplicationContext());
		LayoutParams lp = new LayoutParams(100, 100);
		iv.setLayoutParams(lp);
		iv.setPadding(0, 0, 10, 0);
		iv.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(v.getContext(), AttachmentViewerActivity.class);
				intent.putExtra("attachment", a);
				intent.putExtra(AttachmentViewerActivity.EDITABLE, true);
				startActivityForResult(intent, ATTACHMENT_VIEW_ACTIVITY_REQUEST_CODE);
			}
		});
		l.addView(iv);
		
		// get content type from everywhere I can think of
		String contentType = a.getContentType();
		if (contentType == null || "".equalsIgnoreCase(contentType) || "application/octet-stream".equalsIgnoreCase(contentType)) {
			String name = a.getName();
			if (name == null) {
				name = a.getLocalPath();
				if (name == null) {
					name = a.getRemotePath();
				}
			}
			contentType = MediaUtility.getMimeType(name);
		}
		
		if (absPath != null) {
			if (contentType.startsWith("image")) {
				Glide.load(new File(absPath)).placeholder(android.R.drawable.progress_indeterminate_horizontal).centerCrop().into(iv);
			} else if (contentType.startsWith("video")) {
				Glide.load(R.drawable.ic_video_2x).into(iv);
			} else if (contentType.startsWith("audio")) {
				Glide.load(R.drawable.ic_microphone).into(iv);
			}
		} else if (remoteId != null) {
			String url = a.getUrl();
			Log.i("test", "url to load is: " + url);
			Log.i("test", "content type is: " + contentType + " name is: " + a.getName());
			if (contentType.startsWith("image")) {
				Glide.load(url).placeholder(android.R.drawable.progress_indeterminate_horizontal).centerCrop().into(iv);
			} else if (contentType.startsWith("video")) {
				Glide.load(R.drawable.ic_video_2x).into(iv);
			} else if (contentType.startsWith("audio")) {
				Glide.load(R.drawable.ic_microphone).into(iv);
			}
		}
		
		
		
//		try {
//			
//			if (absPath.endsWith(".mp4")) {
//				Drawable[] layers = new Drawable[2];
//				Resources r = getResources();
//				layers[0] = new BitmapDrawable(r, ThumbnailUtils.createVideoThumbnail(absPath, MediaStore.Video.Thumbnails.MICRO_KIND));
//				layers[1] = r.getDrawable(R.drawable.ic_video_white_2x);
//				LayerDrawable ld = new LayerDrawable(layers);
//				iv.setImageDrawable(ld);
//			} else if (absPath.endsWith(".mp3") || absPath.endsWith("m4a")) {
//				iv.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_microphone));
//			} else {
//				iv.setImageBitmap(MediaUtility.getThumbnail(new File(absPath), 100));
//			}
//			LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
//			iv.setLayoutParams(lp);
//			iv.setPadding(0, 0, 10, 0);
//			iv.setOnClickListener(new View.OnClickListener() {
//
//				@Override
//				public void onClick(View v) {
//					Intent intent = new Intent(v.getContext(), AttachmentViewerActivity.class);
//					intent.setData(Uri.fromFile(new File(absPath)));
//					intent.putExtra(AttachmentViewerActivity.EDITABLE, true);
//					startActivityForResult(intent, ATTACHMENT_VIEW_ACTIVITY_REQUEST_CODE);
//				}
//			});
//			l.addView(iv);
//			Log.d("image", "Set the image gallery to have an image with absolute path " + absPath);
//		} catch (Exception e) {
//			Log.e("exception", "Error making image", e);
//		}
	}
	
	Uri currentImageUri;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != RESULT_OK)
			return;
		switch (requestCode) {
		case CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE:
			MediaUtility.addImageToGallery(getApplicationContext(), currentImageUri);
			Attachment capture = new Attachment();
			capture.setLocalPath(MediaUtility.getFileAbsolutePath(currentImageUri, this));
			attachments.add(capture);
			addAttachmentToGallery(capture);
			break;
		case CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE:
		case GALLERY_ACTIVITY_REQUEST_CODE:
		case CAPTURE_VOICE_ACTIVITY_REQUEST_CODE:
			List<Uri> uris = getUris(data);
			for (Uri u : uris) {
				String path = MediaUtility.getPath(getApplicationContext(), u);
				Attachment a = new Attachment();
				a.setLocalPath(path);
				attachments.add(a);
				addAttachmentToGallery(a);
			}
			break;
		case ATTACHMENT_VIEW_ACTIVITY_REQUEST_CODE:
			Attachment remove = data.getParcelableExtra("attachment");
			if (remove != null && data.getBooleanExtra("REMOVE", false)) {
				int idx = attachments.indexOf(remove);
				attachments.remove(remove);
				LinearLayout l = (LinearLayout) findViewById(R.id.image_gallery);
				l.removeViewAt(idx);
			}
			break;
		case LOCATION_EDIT_ACTIVITY_REQUEST_CODE:
			l = data.getParcelableExtra(LocationEditActivity.LOCATION);
			setupMap();
			break;
		}
	}

	private List<Uri> getUris(Intent intent) {
		List<Uri> uris = new ArrayList<Uri>();
		addClipDataUris(intent, uris);
		if (intent.getData() != null) {
			uris.add(intent.getData());
		}
		return uris;
	}
	
	@TargetApi(16)
	private void addClipDataUris(Intent intent, List<Uri> uris) {
		if (Build.VERSION.SDK_INT >= 16) {
			ClipData cd = intent.getClipData();
			if (cd == null) return;
			for (int i = 0; i < cd.getItemCount(); i++) {
				uris.add(cd.getItemAt(i).getUri());
			}
		}
	}

	public void onTypeOrLevelChanged(String field, String value) {
		o.addProperties(Collections.singleton(new ObservationProperty(field, value)));
		if (observationMarker != null) {
			observationMarker.remove();
		}
		observationMarker = map.addMarker(new MarkerOptions().position(new LatLng(l.getLatitude(), l.getLongitude())).icon(ObservationBitmapFactory.bitmapDescriptor(this, o)));
	}
}
