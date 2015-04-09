package org.kore.kolabnotes.android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.accountswitcher.AccountHeader;
import com.mikepenz.materialdrawer.model.BaseDrawerItem;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;

import org.kore.kolab.notes.Note;
import org.kore.kolab.notes.Notebook;
import org.kore.kolab.notes.NotesRepository;
import org.kore.kolab.notes.local.LocalNotesRepository;
import org.kore.kolab.notes.v3.KolabNotesParserV3;
import org.kore.kolabnotes.android.adapter.NoteAdapter;
import org.kore.kolabnotes.android.content.NoteRepository;
import org.kore.kolabnotes.android.content.NotebookRepository;
import org.kore.kolabnotes.android.content.TagRepository;
import org.kore.kolabnotes.android.itemanimator.CustomItemAnimator;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainPhoneActivity extends ActionBarActivity {

    private final DrawerItemClickedListener drawerItemClickedListener = new DrawerItemClickedListener();

    private List<Note> notesList = new ArrayList<Note>();

    private NoteAdapter mAdapter;
    private ImageButton mFabButton;
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Drawer.Result mDrawer;

    private NoteRepository notesRepository = new NoteRepository(this);
    private NotebookRepository notebookRepository = new NotebookRepository(this);
    private TagRepository tagRepository = new TagRepository(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_phone);

        // Set explode animation when enter and exit the activity
        //Utils.configureWindowEnterExitTransition(getWindow());

        // Handle Toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        AccountHeader.Result headerResult = new AccountHeader()
                .withActivity(this)
                .withHeaderBackground(R.drawable.drawer_header_background)
                .addProfiles(
                        new ProfileDrawerItem().withName("Konrad Renner").withEmail("konrad.renner@kolabnow.com")
                )
                .withOnAccountHeaderListener(new AccountHeader.OnAccountHeaderListener() {
                    @Override
                    public boolean onProfileChanged(View view, IProfile profile, boolean currentProfile) {
                        return false;
                    }
                })
                .build();

        mDrawer = new Drawer()
                .withActivity(this)
                .withToolbar(toolbar)
                .withAccountHeader(headerResult)
                .addDrawerItems(
                        new PrimaryDrawerItem().withName(getResources().getString(R.string.drawer_item_tags)).withTag("HEADING_TAG").setEnabled(false).withDisabledTextColor(R.color.material_drawer_dark_header_selection_text),
                        new SecondaryDrawerItem().withName(getResources().getString(R.string.drawer_item_alltags)).withTag("ALL_TAG"),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withName(getResources().getString(R.string.drawer_item_notebooks)).withTag("HEADING_NOTEBOOK").setEnabled(false).withDisabledTextColor(R.color.material_drawer_dark_header_selection_text),
                        new SecondaryDrawerItem().withName(getResources().getString(R.string.drawer_item_allnotes)).withTag("ALL_NOTEBOOK")

                )
                .withOnDrawerItemClickListener(drawerItemClickedListener)
                .build();

        mDrawer.setSelection(4);
        // Fab Button
        mFabButton = (ImageButton) findViewById(R.id.fab_button);
        //mFabButton.setImageDrawable(new IconicsDrawable(this, FontAwesome.Icon.faw_upload).color(Color.WHITE).actionBarSize());
        mFabButton.setOnClickListener(fabClickListener);
        Utils.configureFab(mFabButton);
        mFabButton.setOnClickListener(new CreateButtonListener());

        mRecyclerView = (RecyclerView) findViewById(R.id.list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(new CustomItemAnimator());
        //mRecyclerView.setItemAnimator(new ReboundItemAnimator());

        mAdapter = new NoteAdapter(new ArrayList<Note>(), R.layout.row_application, this);
        mRecyclerView.setAdapter(mAdapter);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.theme_accent));
        mSwipeRefreshLayout.setRefreshing(true);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new InitializeApplicationsTask().execute();
            }
        });

        new InitializeApplicationsTask().execute();

        if (savedInstanceState != null) {
            //nothing at the moment
        }

        //show progress
        mRecyclerView.setVisibility(View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.create_notebook_menu:
                AlertDialog newNBDialog = createNotebookDialog();
                newNBDialog.show();
                break;
            case R.id.create_tag_menu:
                AlertDialog newTagDialog = createTagDialog();
                newTagDialog.show();
                break;
            case R.id.settings_menu:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
        }
        return true;
    }

    private AlertDialog createNotebookDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.dialog_input_text_notebook);

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_text_input, null);

        builder.setView(view);

        builder.setPositiveButton(R.string.ok,new CreateNotebookButtonListener((EditText)view.findViewById(R.id.dialog_text_input_field)));
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //nothing
            }
        });
        return builder.create();
    }

    private AlertDialog createTagDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.dialog_input_text_tag);

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_text_input, null);

        builder.setView(view);

        builder.setPositiveButton(R.string.ok,new CreateTagButtonListener((EditText)view.findViewById(R.id.dialog_text_input_field)));
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //nothing
            }
        });
        return builder.create();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    View.OnClickListener fabClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            //nothing at the moment
        }
    };


    public void animateActivity(Note appInfo, View appIcon) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra("appInfo", appInfo.getSummary());

        ActivityOptionsCompat transitionActivityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(this, Pair.create((View) mFabButton, "fab"), Pair.create(appIcon, "appIcon"));
        startActivity(i, transitionActivityOptions.toBundle());
    }


    private class DrawerItemClickedListener implements Drawer.OnDrawerItemClickListener{
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l, IDrawerItem iDrawerItem) {
            changeNoteSelection();
        }

        public void changeNoteSelection(){
            //TODO
        }
    }

    private class InitializeApplicationsTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            mAdapter.clearNotes();
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            notesList.clear();

            //Query the notes
            final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<Note> notes = notesRepository.getAll();
            for (Note note : notes) {
                notesList.add(note);
            }
            Collections.sort(notesList);
            notesRepository.close();

            //Query the tags
            for (String tag : tagRepository.getAll()) {
                mDrawer.addItem(new PrimaryDrawerItem().withName(tag).withTag("TAG"));
            }

            //Query the notebooks
            for (Notebook notebook : notebookRepository.getAll()) {
                mDrawer.addItem(new SecondaryDrawerItem().withName(notebook.getSummary()).withTag("NOTEBOOK"));
            }

            orderDrawerItems(mDrawer);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            //handle visibility
            mRecyclerView.setVisibility(View.VISIBLE);

            //set data for list
            mAdapter.addNotes(notesList);
            mSwipeRefreshLayout.setRefreshing(false);

            super.onPostExecute(result);
        }

    }

    class CreateButtonListener implements View.OnClickListener{
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(MainPhoneActivity.this,DetailActivity.class);

            startActivity(intent);
        }
    }

    public class CreateTagButtonListener implements DialogInterface.OnClickListener{
        private final EditText textField;

        public CreateTagButtonListener(EditText textField) {
            this.textField = textField;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if(textField == null || textField.getText() == null || textField.getText().toString().trim().length() == 0){
                return;
            }

            String value = textField.getText().toString();

            tagRepository.insert(value);

            mDrawer.addItem(new SecondaryDrawerItem().withName(value).withTag("TAG"));

            orderDrawerItems(mDrawer);
        }
    }

    public class CreateNotebookButtonListener implements DialogInterface.OnClickListener{

        private final EditText textField;

        public CreateNotebookButtonListener(EditText textField) {
            this.textField = textField;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if(textField == null || textField.getText() == null || textField.getText().toString().trim().length() == 0){
                return;
            }

            Note.Identification ident = new Note.Identification(UUID.randomUUID().toString(),"kolabnotes-android");
            Timestamp now = new Timestamp(System.currentTimeMillis());
            Note.AuditInformation audit = new Note.AuditInformation(now,now);

            String value = textField.getText().toString();

            Notebook nb = new Notebook(ident,audit, Note.Classification.PUBLIC, value);
            nb.setDescription(value);
            notebookRepository.insert(nb);

            mDrawer.addItem(new SecondaryDrawerItem().withName(value).withTag("NOTEBOOK"));

            orderDrawerItems(mDrawer, value);
        }
    }

    void orderDrawerItems(Drawer.Result drawer){
        orderDrawerItems(drawer,null);
    }

    void orderDrawerItems(Drawer.Result drawer, String selectionName){
        ArrayList<IDrawerItem> items = drawer.getDrawerItems();

        List<String> tags = new ArrayList<>();
        List<String> notebooks = new ArrayList<>();

        boolean notebookSelected = true;
        String selected = null;

        int selection = drawer.getCurrentSelection();
        for(IDrawerItem item : items){
            if(item instanceof BaseDrawerItem){
                BaseDrawerItem base = (BaseDrawerItem)item;

                String type = base.getTag().toString();
                if(type.equalsIgnoreCase("TAG")){
                    tags.add(base.getName());
                    if(selection == 0){
                        notebookSelected = false;
                        selected = base.getName();
                    }
                }else if(type.equalsIgnoreCase("NOTEBOOK")){
                    notebooks.add(base.getName());
                    if(selection == 0){
                        selected = base.getName();
                    }
                }else if(type.equalsIgnoreCase("ALL_NOTEBOOK")){
                    if(selection == 0){
                        selected = base.getName();
                    }
                }else if(type.equalsIgnoreCase("ALL_TAG")){
                    if(selection == 0){
                        selected = base.getName();
                    }
                }
            }
            selection--;
        }

        if(selectionName != null){
            selected = selectionName;
            notebookSelected = true;
        }

        Collections.sort(tags);
        Collections.sort(notebooks);

        drawer.getDrawerItems().clear();

        drawer.getDrawerItems().add(new PrimaryDrawerItem().withName(getResources().getString(R.string.drawer_item_tags)).withTag("HEADING_TAG").setEnabled(false).withDisabledTextColor(R.color.material_drawer_dark_header_selection_text));
        drawer.getDrawerItems().add(new SecondaryDrawerItem().withName(getResources().getString(R.string.drawer_item_alltags)).withTag("ALL_TAG"));

        int idx = 1;
        for(String tag : tags){
            drawer.getDrawerItems().add(new SecondaryDrawerItem().withName(tag).withTag("TAG"));

            idx++;
            if(!notebookSelected && tag.equalsIgnoreCase(selected)){
                selection = idx;
            }
        }

        drawer.getDrawerItems().add(new DividerDrawerItem());

        drawer.getDrawerItems().add(new PrimaryDrawerItem().withName(getResources().getString(R.string.drawer_item_notebooks)).withTag("HEADING_NOTEBOOK").setEnabled(false).withDisabledTextColor(R.color.material_drawer_dark_header_selection_text));
        drawer.getDrawerItems().add(new SecondaryDrawerItem().withName(getResources().getString(R.string.drawer_item_allnotes)).withTag("ALL_NOTEBOOK"));

        idx = idx+3;
        if(notebookSelected){
            selection = idx;
        }
        for(String notebook : notebooks){
            drawer.getDrawerItems().add(new SecondaryDrawerItem().withName(notebook).withTag("NOTEBOOK"));

            idx++;
            if(notebookSelected && notebook.equalsIgnoreCase(selected)){
                selection = idx;
            }
        }

        drawer.setSelection(selection);
        drawerItemClickedListener.changeNoteSelection();
    }
}
