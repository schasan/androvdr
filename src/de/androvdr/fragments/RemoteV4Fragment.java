package de.androvdr.fragments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import de.androvdr.Preferences;
import de.androvdr.R;

public class RemoteV4Fragment extends RemoteFragment {
	private static transient Logger logger = LoggerFactory.getLogger(RemoteV4Fragment.class);

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View root = inflater.inflate(R.layout.main, container, false);

		MyTabContentFactory tab = new MyTabContentFactory(inflater);
	    TabHost mTabHost = (TabHost) root.findViewById(R.id.tabhost);
	    mTabHost.setup();
	    
	    if (Preferences.alternateLayout) {
		    mTabHost.addTab(mTabHost.newTabSpec("tab_main").setIndicator(getString(R.string.tab_main_text)).setContent(tab));
	        mTabHost.addTab(mTabHost.newTabSpec("tab_numerics").setIndicator(getString(R.string.tab_numerics_text)).setContent(tab));

	        switch (Preferences.screenSize) {
	        case Preferences.SCREENSIZE_SMALL:
	        case Preferences.SCREENSIZE_NORMAL:
		        mTabHost.addTab(mTabHost.newTabSpec("tab_play").setIndicator(getString(R.string.tab_play_text)).setContent(tab));
	        	break;
	        }
	    } else {
		    mTabHost.addTab(mTabHost.newTabSpec("tab_rc1").setIndicator(getString(R.string.tab1_text)).setContent(tab));
	        mTabHost.addTab(mTabHost.newTabSpec("tab_rc2").setIndicator(getString(R.string.tab2_text)).setContent(tab));
	        mTabHost.addTab(mTabHost.newTabSpec("tab_rc3").setIndicator(getString(R.string.tab3_text)).setContent(tab));
	    }
	    
        if(mUsertabFile.exists()){
        	logger.trace("fuege User-Tab hinzu");
        	mTabHost.addTab(mTabHost.newTabSpec("tab_rc5").setIndicator("User").setContent(tab));
        }

        int tabheight = (mTabHost.getTabWidget().getChildAt(0).getLayoutParams().height / 3) * 2;
        for ( int i = 0; i < mTabHost.getTabWidget().getChildCount(); i++)
	        mTabHost.getTabWidget().getChildAt(i).getLayoutParams().height = tabheight;
        
        mTabHost.setCurrentTab(0);        

        // registerForContextMenu(root.findViewById(android.R.id.list));
		return root;
	}
	
    public class MyTabContentFactory implements TabContentFactory {
    	LayoutInflater mInflater;
    	
    	public MyTabContentFactory(LayoutInflater inflater){
    		mInflater = inflater;
    	}
    	
    	@Override
    	public View createTabContent(String tag) {
    		View root;
    		logger.trace("Erstelle den Tab {}", tag);
    		
    		if(tag.equals("tab_rc1")){
    			root = mInflater.inflate(R.layout.tab1, null);
    	        addClickListeners(root);
    	        return root.findViewById(R.id.tab1);
    		}
    		if(tag.equals("tab_rc2")){
    			root = mInflater.inflate(R.layout.tab2, null);
    	        addClickListeners(root);
    			return root.findViewById(R.id.tab2);
    		}
    		if(tag.equals("tab_rc3")){
    			root = mInflater.inflate(R.layout.tab3, null);
    	        addClickListeners(root);
    			return root.findViewById(R.id.tab3);
    		}
    		if(tag.equals("tab_rc5")){
    			root = getUserScreen();
    	        addClickListeners(root);
    	        return root;
    		}
    		if(tag.equals("tab_main")){
    			root = mInflater.inflate(R.layout.remote_vdr_main, null);
    	        addClickListeners(root);
    			return root.findViewById(R.id.remote_vdr_main_id);
    		}
    		if(tag.equals("tab_numerics")){
    			root = mInflater.inflate(R.layout.remote_vdr_numerics, null);
    	        addClickListeners(root);
    			return root.findViewById(R.id.remote_vdr_numerics_id);
    		}
    		if(tag.equals("tab_play")){
    			root = mInflater.inflate(R.layout.remote_vdr_play, null);
    	        addClickListeners(root);
    			return root.findViewById(R.id.remote_vdr_play_id);
    		}
    		return null;
    	}
    }
}
