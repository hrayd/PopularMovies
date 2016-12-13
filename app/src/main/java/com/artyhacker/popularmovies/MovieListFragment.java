package com.artyhacker.popularmovies;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

import com.artyhacker.popularmovies.adapter.MovieListAdapter;
import com.artyhacker.popularmovies.bean.MovieBean;
import com.artyhacker.popularmovies.common.ApiConfig;
import com.artyhacker.popularmovies.db.MovieListDaoUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MovieListFragment extends Fragment implements AdapterView.OnItemClickListener {

    private static final int REQUEST_SUCCESS = 1;
    private static final int REQUEST_FAIL = 0;
    private String moviesBaseUrl = "";

    private ArrayList<MovieBean> movieBeanArray;
    private GridView gridView;

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REQUEST_SUCCESS:
                    gridView.setAdapter(new MovieListAdapter(getActivity(), movieBeanArray, gridView));
                    new MovieListDaoUtils(getContext()).saveMovieList(movieBeanArray);
                    break;
                case REQUEST_FAIL:
                    Toast.makeText(getActivity(), R.string.MSG_NETWORK_ERROR, Toast.LENGTH_SHORT).show();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    public MovieListFragment() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        setHasOptionsMenu(true);
        movieBeanArray = new ArrayList<MovieBean>();
        getMoviesList();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_popular_movies, container, false);
        gridView = (GridView) rootView.findViewById(R.id.fragment_grid_layout);
        gridView.setOnItemClickListener(this);
        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_popularmoviesfragment, menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_refresh:
                getMoviesList();
                break;
            case R.id.menu_setting:
                Intent intent = new Intent(getActivity(), SettingActivity.class);
                startActivityForResult(intent, 0);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode) {
            case 1:
                getMoviesList();
                break;
            default:
                break;
        }
    }

    public void getMoviesList() {
        movieBeanArray = new ArrayList<MovieBean>();
        //new RefreshMoviesTask().execute();
        getMovieListFromNetwork();
        //movieListDaoUtils.saveMovieList(movieBeanArray);
    }

    private URL getMovieListUrl(){

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String sortType = prefs.getString(getString(R.string.pref_sortType_key), getString(R.string.pref_sortType_default));
        if ("0".equals(sortType)) {
            moviesBaseUrl = ApiConfig.GET_MOVIES_POPULAR_BASE_URL;
        }
        else if ("1".equals(sortType)) {
            moviesBaseUrl = ApiConfig.GET_MOVIES_TOP_RATED_BASE_URL;
        }
        Uri builtUri = Uri.parse(moviesBaseUrl).buildUpon()
                .appendQueryParameter(ApiConfig.API_KEY_PARAM, ApiConfig.API_KEY)
                .appendQueryParameter(ApiConfig.PAGE_PARAM, "1")
                .appendQueryParameter(ApiConfig.LANGUAGE_PARAM, ApiConfig.LANGUAGE_VALUE_ZH)
                .build();
        URL url = null;
        try {
            url = new URL(builtUri.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return url;
    }

    public void getMovieListFromNetwork() {

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                    .url(getMovieListUrl())
                    .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Message msg = new Message();
                msg.what = REQUEST_FAIL;
                handler.sendMessage(msg);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String reponseJson = response.body().string();
                getMoviesListFromJson(reponseJson);
                Message msg = new Message();
                msg.what = REQUEST_SUCCESS;
                handler.sendMessage(msg);
            }
        });


    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MovieBean movie = movieBeanArray.get(position);
        Intent intent = new Intent(getActivity(), MovieDetailsActivity.class);
        intent.putExtra("id", movie.id);
        startActivity(intent);
    }

    private void getMoviesListFromJson(String moviesJsonStr) {
        try {
            JSONObject object = new JSONObject(moviesJsonStr);
            JSONArray jsonArray = object.getJSONArray("results");
            for(int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonMovie = jsonArray.getJSONObject(i);
                int id = jsonMovie.getInt("id");
                String title = jsonMovie.getString("title");
                String image = jsonMovie.getString("poster_path");
                String overview = jsonMovie.getString("overview");
                double voteAverage = jsonMovie.getDouble("vote_average");
                String releaseDate = jsonMovie.getString("release_date");
                double popularity = jsonMovie.getDouble("popularity");
                MovieBean bean = new MovieBean();
                bean.id = id;
                bean.title = title;
                bean.image = image;
                bean.overview = overview;
                bean.voteAverage = voteAverage;
                bean.releaseDate = releaseDate;
                bean.popularity = popularity;
                movieBeanArray.add(bean);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            movieBeanArray = new ArrayList<MovieBean>();
            movieBeanArray = new MovieListDaoUtils(getContext()).getMovieListfromDB();
            //new RefreshMoviesTask().execute();
            getMovieListFromNetwork();
        }
    }

    /*
    public class RefreshMoviesTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            String moviesJsonStr = "";

            try {
                URL url = new URL(getMovieListUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setReadTimeout(5000);
                connection.setConnectTimeout(5000);
                InputStream is = connection.getInputStream();
                String strLine = "";
                reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder builder = new StringBuilder();
                while((strLine = reader.readLine()) != null) {
                    builder.append(strLine);
                }
                moviesJsonStr = builder.toString();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if(reader != null)
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                if(connection != null)
                    connection.disconnect();
            }
            if(!moviesJsonStr.isEmpty()) {
                getMoviesListFromJson(moviesJsonStr);
            }
            return null;
        }


        @Override
        protected void onPostExecute(Void aVoid) {
            gridView.setAdapter(new MovieListAdapter(getActivity(), movieBeanArray, gridView));
        }
    }
    */
}
