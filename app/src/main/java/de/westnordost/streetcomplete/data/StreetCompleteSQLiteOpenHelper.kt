package de.westnordost.streetcomplete.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.content.contentValuesOf
import de.westnordost.osmapi.map.data.Element
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.data.user.achievements.UserAchievementsTable
import de.westnordost.streetcomplete.data.user.achievements.UserLinksTable

import javax.inject.Singleton

import de.westnordost.streetcomplete.data.osm.upload.changesets.OpenChangesetsTable
import de.westnordost.streetcomplete.data.osm.elementgeometry.ElementGeometryTable
import de.westnordost.streetcomplete.data.osm.mapdata.NodeTable
import de.westnordost.streetcomplete.data.osm.osmquest.OsmQuestTable
import de.westnordost.streetcomplete.data.osm.splitway.OsmQuestSplitWayTable
import de.westnordost.streetcomplete.data.osm.osmquest.changes.UndoOsmQuestTable
import de.westnordost.streetcomplete.data.osmnotes.createnotes.CreateNoteTable
import de.westnordost.streetcomplete.data.osmnotes.NoteTable
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTable
import de.westnordost.streetcomplete.data.osm.mapdata.WayTable
import de.westnordost.streetcomplete.data.visiblequests.QuestVisibilityTable
import de.westnordost.streetcomplete.data.user.QuestStatisticsTable
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesTable
import de.westnordost.streetcomplete.data.notifications.NewUserAchievementsTable
import de.westnordost.streetcomplete.data.osm.delete_element.DeleteOsmElementTable
import de.westnordost.streetcomplete.data.osm.elementgeometry.ElementGeometryEntryMapping
import de.westnordost.streetcomplete.data.osmnotes.commentnotes.CommentNote
import de.westnordost.streetcomplete.data.osmnotes.commentnotes.CommentNoteMapping
import de.westnordost.streetcomplete.data.osmnotes.commentnotes.CommentNoteTable
import de.westnordost.streetcomplete.data.osmnotes.notequests.NoteQuestsHiddenTable
import de.westnordost.streetcomplete.data.user.CountryStatisticsTable
import de.westnordost.streetcomplete.ktx.*
import de.westnordost.streetcomplete.quests.road_name.data.RoadNamesTable
import de.westnordost.streetcomplete.quests.oneway_suspects.AddSuspectedOneway
import de.westnordost.streetcomplete.quests.oneway_suspects.data.WayTrafficFlowTable
import de.westnordost.streetcomplete.util.Serializer
import java.util.*
import javax.inject.Inject

@Singleton class StreetCompleteSQLiteOpenHelper(context: Context, dbName: String) :
    SQLiteOpenHelper(context, dbName, null, DB_VERSION) {

    @Inject internal lateinit var elementGeometryEntryMapping: ElementGeometryEntryMapping
    @Inject internal lateinit var commentNoteMapping: CommentNoteMapping
    @Inject internal lateinit var serializer: Serializer

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(ElementGeometryTable.CREATE)
        db.execSQL(OsmQuestTable.CREATE)

        db.execSQL(UndoOsmQuestTable.CREATE)

        db.execSQL(NodeTable.CREATE)
        db.execSQL(WayTable.CREATE)
        db.execSQL(RelationTable.CREATE)

        db.execSQL(NoteTable.CREATE)
        db.execSQL(NoteQuestsHiddenTable.CREATE)
        db.execSQL(CreateNoteTable.CREATE)
        db.execSQL(CommentNoteTable.CREATE)

        db.execSQL(QuestStatisticsTable.CREATE)
        db.execSQL(CountryStatisticsTable.CREATE)
        db.execSQL(UserAchievementsTable.CREATE)
        db.execSQL(UserLinksTable.CREATE)
        db.execSQL(NewUserAchievementsTable.CREATE)

        db.execSQL(DownloadedTilesTable.CREATE)

        db.execSQL(OsmQuestTable.CREATE_VIEW)
        db.execSQL(UndoOsmQuestTable.MERGED_VIEW_CREATE)

        db.execSQL(OpenChangesetsTable.CREATE)

        db.execSQL(QuestVisibilityTable.CREATE)

        db.execSQL(OsmQuestSplitWayTable.CREATE)

        db.execSQL(DeleteOsmElementTable.CREATE)

        db.execSQL(RoadNamesTable.CREATE)
        db.execSQL(WayTrafficFlowTable.CREATE)

    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // in version 2, the commit_message field was added, in version 3, removed again.
        // Unfortunately, dropping a column in SQLite is not possible using ALTER TABLE ... DROP ...
        // so we copy the whole content of the table into a new table
        if (oldVersion == 2) {
            val tableName = OsmQuestTable.NAME
            val oldTableName = tableName + "_old"
            db.execSQL("ALTER TABLE $tableName RENAME TO $oldTableName")
            db.execSQL(OSM_QUEST_TABLE_CREATE_DB_VERSION_3)
            val allColumns = OSM_QUEST_TABLE_ALL_COLUMNS_DB_VERSION_3.joinToString(",")
            db.execSQL("INSERT INTO $tableName ($allColumns) SELECT $allColumns FROM $oldTableName")
            db.execSQL("DROP TABLE $oldTableName")
        }

        if (oldVersion < 3 && newVersion >= 3) {
            db.execSQL(OpenChangesetsTable.CREATE)
        }

        if (oldVersion < 4 && newVersion >= 4) {
            if (!db.hasColumn(OsmQuestTable.NAME, OsmQuestTable.Columns.CHANGES_SOURCE)) {
                db.execSQL("""
                    ALTER TABLE ${OsmQuestTable.NAME}
                    ADD COLUMN ${OsmQuestTable.Columns.CHANGES_SOURCE} varchar(255);
                    """.trimIndent()
                )
            }
            db.execSQL("""
                UPDATE ${OsmQuestTable.NAME}
                SET ${OsmQuestTable.Columns.CHANGES_SOURCE} = 'survey'
                WHERE ${OsmQuestTable.Columns.CHANGES_SOURCE} ISNULL;
                """.trimIndent()
            )

            // sqlite does not support dropping/altering constraints. Need to create new table.
            // For simplicity sake, we just drop the old table and create it anew, this has the
            // effect that all currently open changesets will not be used but instead new ones are
            // created. That's okay because OSM server closes open changesets after 1h automatically.
            db.execSQL("DROP TABLE ${OpenChangesetsTable.NAME};")
            db.execSQL(OpenChangesetsTable.CREATE)
        }

        if (oldVersion < 5 && newVersion >= 5) {
            db.execSQL("""
                ALTER TABLE ${CreateNoteTable.NAME}
                ADD COLUMN ${CreateNoteTable.Columns.QUEST_TITLE} text;
                """.trimIndent()
            )
        }

        if (oldVersion < 6 && newVersion >= 6) {
            db.execSQL(RoadNamesTable.CREATE)
        }

        if (oldVersion < 7 && newVersion >= 7) {
            db.execSQL(UndoOsmQuestTable.CREATE)
            db.execSQL(UndoOsmQuestTable.MERGED_VIEW_CREATE)
        }

        if (oldVersion < 8 && newVersion >= 8) {
            db.execSQL("""
                ALTER TABLE ${CreateNoteTable.NAME}
                ADD COLUMN ${CreateNoteTable.Columns.IMAGE_PATHS} blob;
                """.trimIndent()
            )
            db.execSQL("""
                ALTER TABLE osm_notequests
                ADD COLUMN image_paths blob;
                """.trimIndent()
            )
        }

        if (oldVersion < 9 && newVersion >= 9) {
            db.execSQL(QuestVisibilityTable.CREATE)
        }

        if (oldVersion < 10 && newVersion >= 10) {
            db.execSQL(WayTrafficFlowTable.CREATE)
        }

        // all oneway quest data was invalidated on version 11
        if (oldVersion < 11 && newVersion >= 11) {
            val where = OsmQuestTable.Columns.QUEST_TYPE + " = ?"
            val args = arrayOf(AddSuspectedOneway::class.java.simpleName)
            db.delete(OsmQuestTable.NAME, where, args)
            db.delete(UndoOsmQuestTable.NAME, where, args)
            db.delete(WayTrafficFlowTable.NAME, null, null)
        }

        if (oldVersion < 12 && newVersion >= 12) {
            db.execSQL(OsmQuestSplitWayTable.CREATE)
            // slightly different structure for undo osm quest table. Isn't worth converting
            db.execSQL("DROP TABLE ${UndoOsmQuestTable.NAME}")
            db.execSQL("DROP VIEW ${UndoOsmQuestTable.NAME_MERGED_VIEW}")
            db.execSQL(UndoOsmQuestTable.CREATE)
            db.execSQL(UndoOsmQuestTable.MERGED_VIEW_CREATE)
        }

        if (oldVersion < 13 && newVersion >= 13) {
            db.execSQL(UserAchievementsTable.CREATE)
            db.execSQL(UserLinksTable.CREATE)
            db.execSQL(NewUserAchievementsTable.CREATE)
        }

        if (oldVersion < 14 && newVersion >= 14) {
            db.execSQL(CountryStatisticsTable.CREATE)
        }

        if (oldVersion < 15 && newVersion >= 15) {
            db.execSQL("""
                ALTER TABLE ${OsmQuestSplitWayTable.NAME}
                ADD COLUMN quest_types_on_way text;
                """.trimIndent()
            )
        }

        if (oldVersion < 16 && newVersion >= 16) {
            /* there was an indication that relations downloaded and serialized with v22.0-beta1 and
               v22.0 might have corrupt relation members. So to be on the safe side, we better clean
               ALL the relations currently in the store. See #2014
             */
            db.execSQL("""
                DELETE FROM ${OsmQuestTable.NAME}
                WHERE ${OsmQuestTable.Columns.ELEMENT_TYPE} = "${Element.Type.RELATION.name}"
            """.trimIndent())
            db.execSQL("""
                DELETE FROM ${UndoOsmQuestTable.NAME}
                WHERE ${UndoOsmQuestTable.Columns.ELEMENT_TYPE} = "${Element.Type.RELATION.name}"
            """.trimIndent())
            db.execSQL("""
                DELETE FROM ${RelationTable.NAME}
            """.trimIndent())
        }

        if (oldVersion < 17 && newVersion >= 17) {
            db.execSQL("""
                ALTER TABLE ${RoadNamesTable.NAME}
                ADD COLUMN ${RoadNamesTable.Columns.LAST_UPDATE} int NOT NULL default ${Date().time};
                """.trimIndent())
        }

        if (oldVersion < 18 && newVersion >= 18) {
            // QUEST_TILE_ZOOM changed
            db.execSQL("DELETE FROM ${DownloadedTilesTable.NAME}")
        }

        if (oldVersion < 19 && newVersion >= 19) {
            db.execSQL(DeleteOsmElementTable.CREATE)

            db.execSQL("""
                DELETE FROM ${OsmQuestTable.NAME} WHERE ${OsmQuestTable.Columns.QUEST_STATUS} = "REVERT"
            """.trimIndent())
        }

        if (oldVersion < 20 && newVersion >= 20) {
            // clearing quests that previously existed but now not anymore
            db.execSQL("""
                DELETE FROM ${OsmQuestTable.NAME}
                WHERE ${OsmQuestTable.Columns.QUEST_TYPE}
                IN (
                    "DetailRoadSurface",
                    "AddTrafficSignalsBlindFeatures",
                    "AddAccessibleForPedestrians",
                    "AddWheelChairAccessPublicTransport",
                    "AddWheelChairAccessToilets"
                )
            """.trimIndent())
        }

        if (oldVersion < 21 && newVersion >= 21) {
            // the new columns min latitude, max latitude, min longitude, max longitude have been
            // added. Need to get all data from the table, drop the table, recreate it and re-insert
            // all the data - this will also fill in the mentioned values correctly
            val entries = db.query(ElementGeometryTable.NAME) { elementGeometryEntryMapping.toObject(it) }
            db.execSQL("DROP TABLE ${ElementGeometryTable.NAME}")
            db.execSQL(ElementGeometryTable.CREATE)
            db.transaction {
                for (entry in entries) {
                    db.replaceOrThrow(ElementGeometryTable.NAME, null, elementGeometryEntryMapping.toContentValues(entry))
                }
            }

            // note table has a last update column now
            db.execSQL("""
                ALTER TABLE ${NoteTable.NAME}
                ADD COLUMN ${NoteTable.Columns.LAST_UPDATE} int NOT NULL default ${Date().time};
            """.trimIndent())

            // node table, way table, relation tabel has a last update column now
            db.execSQL("""
                ALTER TABLE ${NodeTable.NAME}
                ADD COLUMN ${NodeTable.Columns.LAST_UPDATE} int NOT NULL default ${Date().time};
            """.trimIndent())
            db.execSQL("""
                ALTER TABLE ${WayTable.NAME}
                ADD COLUMN ${WayTable.Columns.LAST_UPDATE} int NOT NULL default ${Date().time};
            """.trimIndent())
            db.execSQL("""
                ALTER TABLE ${RelationTable.NAME}
                ADD COLUMN ${RelationTable.Columns.LAST_UPDATE} int NOT NULL default ${Date().time};
            """.trimIndent())

            // OsmNoteQuestTable is no more
            // there is the CommentNote table now....
            val commentNotes = db.query(
                "osm_notequests_full",
                null,
                "quest_status = ANSWERED"
            ) { c -> CommentNote(
                c.getLong("note_id"),
                OsmLatLon(c.getDouble("latitude"), c.getDouble("longitude")),
                c.getString("changes"),
                c.getBlobOrNull(CommentNoteTable.Columns.IMAGE_PATHS)?.let { serializer.toObject<ArrayList<String>>(it) }
            ) }
            db.execSQL(CommentNoteTable.CREATE)
            db.transaction {
                for (commentNote in commentNotes) {
                    db.insert(CommentNoteTable.NAME, null, commentNoteMapping.toContentValues(commentNote))
                }
            }
            // and there is the HiddenNoteQuestTable now
            val hiddenNoteIds = db.query(
                "osm_notequests",
                arrayOf("note_id"),
                "quest_status = HIDDEN"
            ) { it.getLong(0) }
            db.execSQL(NoteQuestsHiddenTable.CREATE)
            db.transaction {
                for (noteId in hiddenNoteIds) {
                    db.insert(NoteQuestsHiddenTable.NAME, null, contentValuesOf(
                        NoteQuestsHiddenTable.Columns.NOTE_ID to noteId
                    ))
                }
            }

            db.execSQL("DROP VIEW osm_notequests_full")
            db.execSQL("DROP TABLE osm_notequests")

            // quest_types_on_way column in osm_split_ways table is no more
            db.execSQL("DROP TABLE ${OsmQuestSplitWayTable.NAME}")
            db.execSQL(OsmQuestSplitWayTable.CREATE)
        }

        // for later changes to the DB
        // ...
    }
}

private val OSM_QUEST_TABLE_ALL_COLUMNS_DB_VERSION_3 = listOf(
    "quest_id",
    "quest_type",
    "element_id",
    "element_type",
    "quest_status",
    "tag_changes",
    "last_update"
)

private val OSM_QUEST_TABLE_CREATE_DB_VERSION_3 = """
    CREATE TABLE osm_quests (
        quest_id INTEGER PRIMARY KEY,
        quest_type varchar(255) NOT NULL,
        quest_status varchar(255) NOT NULL,
        tag_changes blob,
        last_update int NOT NULL,
        element_id int NOT NULL,
        element_type varchar(255) NOT NULL,
        CONSTRAINT same_osm_quest UNIQUE (
            quest_type,
            element_id,
            element_type
        ),
        CONSTRAINT element_key FOREIGN KEY (
            element_type, element_id
        ) REFERENCES elements_geometry (
            element_type, element_id
        )
    );
""".trimIndent()

private const val DB_VERSION = 21
