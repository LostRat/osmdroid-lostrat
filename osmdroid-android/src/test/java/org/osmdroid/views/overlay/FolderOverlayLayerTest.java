package org.osmdroid.views.overlay;

import android.graphics.Canvas;
import org.junit.Assert;
import org.junit.Test;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.DefaultOverlayManager.OverlayLayer;

import java.util.ArrayList;
import java.util.List;

public class FolderOverlayLayerTest {

    private static class TestOverlay extends Overlay {
        @Override
        public void draw(Canvas c, Projection p) {}
    }

    @Test
    public void testFolderContentLayerAssignment() {
        DefaultOverlayManager manager = new DefaultOverlayManager(null);
        FolderOverlay folder = new FolderOverlay();
        
        TestOverlay item1 = new TestOverlay();
        TestOverlay item2 = new TestOverlay();
        
        folder.add(item1);
        folder.add(item2);
        
        manager.add(folder);
        
        // Verify parent tracking
        Assert.assertEquals("Item1 parent should be folder", folder, item1.getParent());
        Assert.assertEquals("Item2 parent should be folder", folder, item2.getParent());
    }

    @Test
    public void testHierarchyVisibility() {
        FolderOverlay folder = new FolderOverlay();
        TestOverlay item = new TestOverlay();
        folder.add(item);
        
        Assert.assertTrue("Item should be enabled by default", item.isEnabled());
        Assert.assertTrue("Folder should be enabled by default", folder.isEnabled());
        Assert.assertTrue("Hierarchy should be enabled", item.isHierarchyEnabled());
        
        folder.setEnabled(false);
        
        Assert.assertTrue("Item itself is still enabled", item.isEnabled());
        Assert.assertFalse("Folder is disabled", folder.isEnabled());
        Assert.assertFalse("Hierarchy should be disabled", item.isHierarchyEnabled());
    }
    
    @Test
    public void testNestedHierarchyVisibility() {
        FolderOverlay rootFolder = new FolderOverlay();
        FolderOverlay subFolder = new FolderOverlay();
        TestOverlay item = new TestOverlay();
        
        subFolder.add(item);
        rootFolder.add(subFolder);
        
        Assert.assertEquals("Item parent is subFolder", subFolder, item.getParent());
        Assert.assertEquals("SubFolder parent is rootFolder", rootFolder, subFolder.getParent());
        
        Assert.assertTrue(item.isHierarchyEnabled());
        
        rootFolder.setEnabled(false);
        Assert.assertFalse("Item should be disabled via root folder", item.isHierarchyEnabled());
        
        rootFolder.setEnabled(true);
        Assert.assertTrue(item.isHierarchyEnabled());
        
        subFolder.setEnabled(false);
        Assert.assertFalse("Item should be disabled via sub folder", item.isHierarchyEnabled());
    }
}
