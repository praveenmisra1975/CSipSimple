/**
 * Copyright (C) 2010-2012 Regis Montoya (aka r3gis - www.r3gis.fr)
 * This file is part of CSipSimple.
 *
 *  CSipSimple is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  If you own a pjsip commercial license you can also redistribute it
 *  and/or modify it under the terms of the GNU Lesser General Public License
 *  as an android library.
 *
 *  CSipSimple is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with CSipSimple.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.csipsimple.ui.prefs.cupcake;

import android.os.Bundle;

import android.app.ActionBar;
import android.view.MenuItem;
import com.csipsimple.ui.prefs.GenericPrefs;
import com.csipsimple.ui.prefs.PrefsLogic;
import com.csipsimple.utils.Compatibility;

public class PrefsLoaderActivity extends GenericPrefs {
    
    private int getPreferenceType() {
        return getIntent().getIntExtra(PrefsLogic.EXTRA_PREFERENCE_TYPE, 0);
    }

    @Override
    protected int getXmlPreferences() {
        return PrefsLogic.getXmlResourceForType(getPreferenceType());
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(PrefsLogic.getTitleResourceForType(getPreferenceType()));
        ActionBar ab = this.getActionBar();
        if(ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == Compatibility.getHomeMenuId()) {
            finish();
            return true;
        }
        return false;
    }

    

    @Override
    protected void afterBuildPrefs() {
        super.afterBuildPrefs();
        PrefsLogic.afterBuildPrefsForType(this, this, getPreferenceType());
        
    }

    @Override
    protected void updateDescriptions() {
        PrefsLogic.updateDescriptionForType(this, this, getPreferenceType());
    }


    
}
