// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.are.viewer.icon;

import java.awt.Point;

import javax.swing.ImageIcon;

import org.infinity.resource.graphics.ColorConvert;

/** A dummy class used as reference for determining the current package name. */
public enum ViewerIcons {
  ICON_BTN_ADD_ACTOR("btn_addActor.png"),
  ICON_BTN_ADD_AMBIENT("btn_addAmbient.png"),
  ICON_BTN_ADD_ANIM("btn_addAnim.png"),
  ICON_BTN_ADD_AUTOMAP("btn_addAutomap.png"),
  ICON_BTN_ADD_CONTAINER("btn_addContainer.png"),
  ICON_BTN_ADD_DOOR("btn_addDoor.png"),
  ICON_BTN_ADD_DOOR_POLY("btn_addDoorPoly.png"),
  ICON_BTN_ADD_ENTRANCE("btn_addEntrance.png"),
  ICON_BTN_ADD_PRO_TRAP("btn_addProTrap.png"),
  ICON_BTN_ADD_REGION("btn_addRegion.png"),
  ICON_BTN_ADD_SPAWN_POINT("btn_addSpawnPoint.png"),
  ICON_BTN_ADD_WALL_POLY("btn_addWallPoly.png"),
  ICON_BTN_EDIT_MODE("btn_editMode.png"),
  ICON_BTN_EXPORT("btn_export.png"),
  ICON_BTN_MAP("btn_map.png"),
  ICON_BTN_MAP_ARE("btn_mapAre.png"),
  ICON_BTN_MAP_WED("btn_mapWed.png"),
  ICON_BTN_REFRESH("btn_refresh.png"),
  ICON_BTN_REST("btn_rest.png"),
  ICON_BTN_SETTINGS("btn_settings.png"),
  ICON_BTN_SONGS("btn_songs.png"),
  ICON_BTN_VIEW_MODE("btn_viewMode.png"),
  ICON_ITM_AMBIENT_G_1("itm_AmbientG1.png", 16, 16),
  ICON_ITM_AMBIENT_G_2("itm_AmbientG2.png", 16, 16),
  ICON_ITM_AMBIENT_L_1("itm_AmbientL1.png", 20, 20),
  ICON_ITM_AMBIENT_L_2("itm_AmbientL2.png", 20, 20),
  ICON_ITM_ANIM_1("itm_Anim1.png", 16, 17),
  ICON_ITM_ANIM_1_BW("itm_Anim1_bw.png", 16, 17),
  ICON_ITM_ANIM_2("itm_Anim2.png", 16, 17),
  ICON_ITM_ANIM_2_BW("itm_Anim2_bw.png", 16, 17),
  ICON_ITM_ANIM_BAM_1("itm_AnimBAM1.png", 16, 17),
  ICON_ITM_ANIM_BAM_1_BW("itm_AnimBAM1_bw.png", 16, 17),
  ICON_ITM_ANIM_BAM_2("itm_AnimBAM2.png", 16, 17),
  ICON_ITM_ANIM_BAM_2_BW("itm_AnimBAM2_bw.png", 16, 17),
  ICON_ITM_ANIM_PVRZ_1("itm_AnimPVRZ1.png", 16, 17),
  ICON_ITM_ANIM_PVRZ_1_BW("itm_AnimPVRZ1_bw.png", 16, 17),
  ICON_ITM_ANIM_PVRZ_2("itm_AnimPVRZ2.png", 16, 17),
  ICON_ITM_ANIM_PVRZ_2_BW("itm_AnimPVRZ2_bw.png", 16, 17),
  ICON_ITM_ANIM_WBM_1("itm_AnimWBM1.png", 16, 17),
  ICON_ITM_ANIM_WBM_1_BW("itm_AnimWBM1_bw.png", 16, 17),
  ICON_ITM_ANIM_WBM_2("itm_AnimWBM2.png", 16, 17),
  ICON_ITM_ANIM_WBM_2_BW("itm_AnimWBM2_bw.png", 16, 17),
  ICON_ITM_ARE_ACTOR_B_1("itm_AreActorB1.png", 12, 40),
  ICON_ITM_ARE_ACTOR_B_2("itm_AreActorB2.png", 12, 40),
  ICON_ITM_ARE_ACTOR_G_1("itm_AreActorG1.png", 12, 40),
  ICON_ITM_ARE_ACTOR_G_2("itm_AreActorG2.png", 12, 40),
  ICON_ITM_ARE_ACTOR_R_1("itm_AreActorR1.png", 12, 40),
  ICON_ITM_ARE_ACTOR_R_2("itm_AreActorR2.png", 12, 40),
  ICON_ITM_AUTOMAP_1("itm_Automap1.png", 26, 26),
  ICON_ITM_AUTOMAP_2("itm_Automap2.png", 26, 26),
  ICON_ITM_CONTAINER_TARGET_1("itm_Container1.png", 13, 29),
  ICON_ITM_CONTAINER_TARGET_2("itm_Container2.png", 13, 29),
  ICON_ITM_CONTAINER_TARGET_L_1("itm_ContainerLaunch1.png", 13, 29),
  ICON_ITM_CONTAINER_TARGET_L_2("itm_ContainerLaunch2.png", 13, 29),
  ICON_ITM_DOOR_TARGET_C_1("itm_DoorClosed1.png", 13, 29),
  ICON_ITM_DOOR_TARGET_C_2("itm_DoorClosed2.png", 13, 29),
  ICON_ITM_DOOR_TARGET_O_1("itm_DoorOpen1.png", 13, 29),
  ICON_ITM_DOOR_TARGET_O_2("itm_DoorOpen2.png", 13, 29),
  ICON_ITM_DOOR_TARGET_L_1("itm_DoorLaunch1.png", 13, 29),
  ICON_ITM_DOOR_TARGET_L_2("itm_DoorLaunch2.png", 13, 29),
  ICON_ITM_ENTRANCE_1("itm_Entrance1.png", 11, 18),
  ICON_ITM_ENTRANCE_2("itm_Entrance2.png", 11, 18),
  ICON_ITM_GAM_ACTOR_B_1("itm_GamActorB1.png", 12, 40),
  ICON_ITM_GAM_ACTOR_B_2("itm_GamActorB2.png", 12, 40),
  ICON_ITM_GAM_ACTOR_G_1("itm_GamActorG1.png", 12, 40),
  ICON_ITM_GAM_ACTOR_G_2("itm_GamActorG2.png", 12, 40),
  ICON_ITM_GAM_ACTOR_R_1("itm_GamActorR1.png", 12, 40),
  ICON_ITM_GAM_ACTOR_R_2("itm_GamActorR2.png", 12, 40),
  ICON_ITM_INI_ACTOR_B_1("itm_IniActorB1.png", 12, 40),
  ICON_ITM_INI_ACTOR_B_2("itm_IniActorB2.png", 12, 40),
  ICON_ITM_INI_ACTOR_G_1("itm_IniActorG1.png", 12, 40),
  ICON_ITM_INI_ACTOR_G_2("itm_IniActorG2.png", 12, 40),
  ICON_ITM_INI_ACTOR_R_1("itm_IniActorR1.png", 12, 40),
  ICON_ITM_INI_ACTOR_R_2("itm_IniActorR2.png", 12, 40),
  ICON_ITM_PRO_TRAP_1("itm_ProTrap1.png", 14, 14),
  ICON_ITM_PRO_TRAP_2("itm_ProTrap2.png", 14, 14),
  ICON_ITM_REGION_TARGET_1("itm_Region1.png", 13, 29),
  ICON_ITM_REGION_TARGET_2("itm_Region2.png", 13, 29),
  ICON_ITM_REGION_TARGET_A_1("itm_RegionActivation1.png", 13, 29),
  ICON_ITM_REGION_TARGET_A_2("itm_RegionActivation2.png", 13, 29),
  ICON_ITM_REGION_TARGET_S_1("itm_RegionSpeaker1.png", 13, 29),
  ICON_ITM_REGION_TARGET_S_2("itm_RegionSpeaker2.png", 13, 29),
  ICON_ITM_SPAWN_POINT_1("itm_SpawnPoint1.png", 22, 22),
  ICON_ITM_SPAWN_POINT_2("itm_SpawnPoint2.png", 22, 22),
  ICON_ITM_TABLE_ENTRANCE_1("itm_TableEntrance1.png", 11, 18),
  ICON_ITM_TABLE_ENTRANCE_2("itm_TableEntrance2.png", 11, 18),
  ICON_ITM_VERTEX_1("itm_Vertex1.png", 3, 3),
  ICON_ITM_VERTEX_2("itm_Vertex2.png", 3, 3);

  private final String fileName;
  private final Point center;
  private ImageIcon icon;

  ViewerIcons(String fileName) {
    this(fileName, 0, 0);
  }

  ViewerIcons(String fileName, int centerX, int centerY) {
    this.fileName = fileName;
    this.center = new Point(centerX, centerY);
  }

  /** Returns the center coordinate of the icon. */
  public Point getCenter() {
    return center;
  }

  /** Returns the {@code ImageIcon} instance of the enum object. */
  public ImageIcon getIcon() {
    if (icon == null) {
      icon = ColorConvert.loadAppIcon(ViewerIcons.class, fileName);
      if (icon == null) {
        throw new NullPointerException("Icon is null");
      }
    }
    return icon;
  }

  /** Returns the name of the graphics file. */
  public String getFileName() {
    return fileName;
  }
}
