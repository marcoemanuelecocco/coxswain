/*
 * Copyright 2015 Sven Meier
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package svenmeier.coxswain;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;

import svenmeier.coxswain.view.ProgramsFragment;
import svenmeier.coxswain.view.WorkoutsFragment;


public class MainActivity extends Activity {

    public static String TAG = "coxswain";

    private ViewPager pager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setContentView(R.layout.layout_main);

        pager = (ViewPager) findViewById(R.id.main_pager);
        pager.setAdapter(new MainAdapter(getFragmentManager()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_programs, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        if (BuildConfig.DEBUG == false) {
            menu.findItem(R.id.action_mock).setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_mock) {
            GymService.start(this, null);

            return true;
        } else if (id == R.id.action_settings) {
            startActivity(SettingsActivity.createIntent(this));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class MainAdapter extends FragmentPagerAdapter {

        public MainAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0) {
                return getString(R.string.programs);
            } else {
                return getString(R.string.workouts);
            }
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
                return new ProgramsFragment();
            } else {
                return new WorkoutsFragment();
            }
        }
    }
}