package ru.spbau.mit.plansnet.dataController;

import android.app.ProgressDialog;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseUser;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ru.spbau.mit.plansnet.MainActivity;
import ru.spbau.mit.plansnet.data.AbstractDataContainer;
import ru.spbau.mit.plansnet.data.Account;
import ru.spbau.mit.plansnet.data.Building;
import ru.spbau.mit.plansnet.data.FloorMap;
import ru.spbau.mit.plansnet.data.UsersGroup;


/**
 * Controller class which manage data and network connection
 */

public class DataController {
    @NonNull
    private NetworkDataManager netManager;
    @NonNull
    private Account userAccount;
    @NonNull
    private Context context;

    private static final String DATA_TAG = "DATA_CONTROLLER_FILES";

    public DataController(@NonNull final Context context,
                          @NonNull final FirebaseUser account) {
        this.context = context;
        netManager = new NetworkDataManager(context, account);

        userAccount = new Account(account.getDisplayName(), account.getUid());
    }

    /**
     * Download maps from server
     */
    public void downloadMaps(@NonNull final List<String> floorsPaths, @NonNull AtomicInteger mapCount) {
        netManager.downloadByPaths(floorsPaths, mapCount, userAccount.getID());
    }

    /**
     * Delete first non null element of hierarchy from local memory and server
     * @param groupName always non null name of group
     * @param buildingName name of building, can be null
     * @param mapName name of map, can be null
     * @throws IllegalArgumentException when there is non null name of elements which doesn't exists
     */
    public void deleteByPath(@NotNull final String groupName,
                             @Nullable final String buildingName,
                             @Nullable final String mapName)
            throws IllegalArgumentException {
        AbstractDataContainer ref = userAccount;

        File mapFile = new File(context.getApplicationContext().getFilesDir().getAbsolutePath() +
                "/" + userAccount.getID() + "/" + groupName);
        AbstractDataContainer next = (AbstractDataContainer) ref.findByName(groupName);
        if (next == null) {
            throw new IllegalArgumentException("Doesn't exists group: " + groupName);
        }
        if (buildingName == null) {
            deleteRecursive(mapFile);
            ref.getAllData().remove(groupName);
            netManager.deleteReference(groupName, null, null);
            return;
        }

        ref = next;
        next = (AbstractDataContainer) ref.findByName(buildingName);
        mapFile = new File(mapFile, buildingName);
        if (next == null) {
            throw new IllegalArgumentException("Doesn't exists building: " + buildingName);
        }
        if (mapName == null) {
            deleteRecursive(mapFile);
            ref.getAllData().remove(buildingName);
            netManager.deleteReference(groupName, buildingName, null);
            return;
        }

        ref = next;
        next = (AbstractDataContainer) ref.findByName(mapName);
        mapFile = new File(mapFile, mapName + ".plannet");
        if (next == null) {
            throw new IllegalArgumentException("Doesn't exists map: " + mapName);
        }
        ref.getAllData().remove(mapName);
        deleteRecursive(mapFile);

        netManager.deleteReference(groupName, buildingName, mapName);
    }

    /**
     * Delete map from local memory and server
     *
     * @param map map which will be deleted
     */
    public void deleteMap(@NonNull final FloorMap map) {
        File mapFile = formingFileFromMap(map);
        if (mapFile.exists()) {
            deleteRecursive(mapFile);
        }

        userAccount.findByName(map.getGroupName())
                .findByName(map.getBuildingName()).getAllData().remove(map);
        netManager.deleteReference(map.getGroupName(), map.getBuildingName(), map.getName());
    }

    /**
     * unsupported operation
     */
    public void renameMap(@NonNull final FloorMap map) {
        throw new UnsupportedOperationException();
    }

    /**
     * Load data from local files to user account
     */
    public void loadLocalFiles() {
        File root = new File(context.getApplicationContext().getFilesDir(), userAccount.getID());
        if (!root.exists()) {
            Log.d(DATA_TAG, "folder for user there isn't exists");
            return;
        }

        for (File group : root.listFiles()) {
            for (File building : group.listFiles()) {
                for (File floor : building.listFiles()) {
                    readMapFromFile(floor);
                }
            }
        }
        Log.d(DATA_TAG, "local files was read");
    }

    /**
     * Method for searching groups on server
     * @param substring substring of names of groups
     * @param ownersAndGroups list for results of searching
     */
    public void getSearchedGroupsAndOwners(@NonNull String substring,
                                           @NonNull final List<MainActivity.SearchResult> ownersAndGroups) {
        CountDownLatch latch = new CountDownLatch(1);
        netManager.getGroupsWhichContainsName(substring, ownersAndGroups, latch);
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void searchGroupMaps(@NonNull String owner, @NonNull String group,
                                @NonNull List<String> floorsPaths, @NonNull AtomicBoolean isFinished) {
        netManager.searchGroupMaps(owner, group, floorsPaths, isFinished);
    }

    /**
     * Download and add to account foreign group from server
     * @param owner id of owner who owned a group
     */
    public void downloadGroup(@NonNull final String owner, @NonNull final List<String> floorsPaths,
                              @NonNull final AtomicInteger mapCount) {
//        netManager.downloadGroup(owner, groupName, floorsPaths, mapCount);
        netManager.downloadByPaths(floorsPaths, mapCount, owner);
    }

    @NonNull
    public Account getAccount() {
        return userAccount;
    }

    @NonNull
    public UsersGroup addGroup(@NonNull final UsersGroup group) {
        return userAccount.setElementToContainer(group);
    }

    @Nullable
    public UsersGroup getGroup(@NonNull final String groupName) {
        return userAccount.findByName(groupName);
    }

    @Nullable
    public FloorMap getMapFromBuilding(@NonNull final Building building,
                                       @NonNull final String mapName) {
        return building.findByName(mapName);
    }

    @NonNull
    public Building addBuildingToGroup(@NonNull final Building building,
                                       @NonNull final UsersGroup group) {
        return group.setElementToContainer(building);
    }

    @Nullable
    public Building getBuildingFromGroup(@NonNull final String buildingName,
                                         @NonNull final UsersGroup group) {
        return group.findByName(buildingName);
    }

    /**
     * Save map to account and file and send it to server
     */
    public void saveMap(@NonNull final FloorMap map)
            throws IllegalArgumentException {
        UsersGroup userGroup = userAccount.findByName(map.getGroupName());
        Log.d("saveMap", "search: " + map.getGroupName());
        if (userGroup == null) {
            throw new IllegalArgumentException("This user haven't group: " + map.getGroupName());
        }

        Building building = userGroup.findByName(map.getBuildingName());
        if (building == null) {
            throw new IllegalArgumentException("User's group '" + map.getGroupName()
                    + "' haven't building: " + map.getBuildingName());
        }

        building.setElementToContainer(map);
        Log.d(DATA_TAG, "set new map to account");


        writeMap(map);
        Toast.makeText(context, "Map saved", Toast.LENGTH_SHORT).show();

        netManager.putMapOnServer(map);
    }

    //private function for writing map to file
    private void writeMap(@NonNull final FloorMap map) {
        File accountFile = formingFileFromMap(map);
        accountFile.getParentFile().mkdirs();

        try (ObjectOutputStream ous = new ObjectOutputStream(new FileOutputStream(accountFile))) {
            ous.writeObject(map);

        } catch (IOException e) {
            Toast.makeText(context, "Can't save a map to the phone", Toast.LENGTH_SHORT).show();
            Log.d(DATA_TAG, "Can't write a map to the phone");
            e.printStackTrace();
        }
    }

    //private function for reading map from file
    private void readMapFromFile(File mapFile) {
        try (ObjectInputStream ois =
                     new ObjectInputStream(new FileInputStream(mapFile))) {
            FloorMap map = (FloorMap) ois.readObject();


            UsersGroup group = userAccount.findByName(map.getGroupName());
            if (group == null) {
                group = userAccount.setElementToContainer(new UsersGroup(map.getGroupName()));
            }
            Building building = group.findByName(map.getBuildingName());
            if (building == null) {
                building = group.setElementToContainer(new Building(map.getBuildingName()));
            }

            building.setElementToContainer(map);


            Log.d(DATA_TAG, "map " + map.getName() + " was read");
        } catch (Exception exception) {
            Log.d(DATA_TAG, "map can't be read");
            exception.printStackTrace();
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }

        fileOrDirectory.delete();
    }

    private File formingFileFromMap(@NonNull final FloorMap map) {
        return new File(context.getApplicationContext().getFilesDir(),
                userAccount.getID() + "/"
                        + map.getGroupName() + "/"
                        + map.getBuildingName() + "/"
                        + map.getName() + ".plannet"
        );
    }

    public void searchMaps(@NonNull List<String> floorsPaths, @NonNull AtomicBoolean isFinished) {
        netManager.searchMaps(floorsPaths, isFinished);
    }
}
