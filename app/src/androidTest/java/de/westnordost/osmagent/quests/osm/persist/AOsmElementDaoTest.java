package de.westnordost.osmagent.quests.osm.persist;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import de.westnordost.osmagent.quests.AndroidDbTestCase;
import de.westnordost.osmapi.map.data.Element;

import static org.mockito.Mockito.*;

public class AOsmElementDaoTest extends AndroidDbTestCase
{
	private static final String TABLE_NAME = "test";
	private static final String ID_COL = "id";
	private static final String VERSION_COL = "version";
	private static final String TESTDB = "testdb.db";

	private TestOsmElementDao dao;
	private SQLiteOpenHelper dbHelper;

	public AOsmElementDaoTest()
	{
		super(TESTDB);
	}

	@Override public void setUp()
	{
		super.setUp();
		dbHelper = new TestDbHelper(getContext());
		dao = new TestOsmElementDao(dbHelper);
	}

	@Override public void tearDown()
	{
		super.tearDown();
		dbHelper.close();
	}

	public void testPutGet()
	{
		dao.put(createElement(6,1));
		assertEquals(6,dao.get(6).getId());
		assertEquals(1,dao.get(6).getVersion());
	}

	public void testPutOverwrite()
	{
		dao.put(createElement(6,0));
		dao.put(createElement(6,5));
		assertEquals(5,dao.get(6).getVersion());
	}

	public void testGetNull()
	{
		assertNull(dao.get(6));
	}

	public void testDelete()
	{
		dao.put(createElement(6,0));
		dao.delete(6);
		assertNull(dao.get(6));
	}

	private class TestDbHelper extends SQLiteOpenHelper
	{
		public TestDbHelper(Context context)
		{
			super(context, TESTDB, null, 1);
		}

		@Override public void onCreate(SQLiteDatabase db)
		{
			// the AOsmElementDao is tied to the quest table... but we only need the id and type
			db.execSQL("CREATE TABLE " + OsmQuestTable.NAME + " (" +
					OsmQuestTable.Columns.ELEMENT_ID +		" int			NOT NULL, " +
					OsmQuestTable.Columns.ELEMENT_TYPE +	" varchar(255)	NOT NULL " +
					");");
			db.execSQL("INSERT INTO "+OsmQuestTable.NAME + " (" +
					OsmQuestTable.Columns.ELEMENT_ID + ", " +
					OsmQuestTable.Columns.ELEMENT_TYPE +	") VALUES " +
					"(1, \""+Element.Type.NODE.name()+"\");");

			db.execSQL("CREATE TABLE "+TABLE_NAME+" ( " +
					ID_COL+" int PRIMARY KEY, " +
					VERSION_COL+" int);");
		}

		@Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
		{

		}
	}

	private class TestOsmElementDao extends AOsmElementDao<Element>
	{
		public TestOsmElementDao(SQLiteOpenHelper dbHelper)
		{
			super(dbHelper);
		}

		@Override protected String getElementTypeName()
		{
			return Element.Type.NODE.name();
		}

		@Override protected String getTableName()
		{
			return TABLE_NAME;
		}

		@Override protected String getIdColumnName()
		{
			return ID_COL;
		}

		@Override protected ContentValues createContentValuesFrom(Element object)
		{
			ContentValues v = new ContentValues();
			v.put(ID_COL, object.getId());
			v.put(VERSION_COL, object.getVersion());
			return v;
		}

		@Override protected Element createObjectFrom(Cursor cursor)
		{
			return createElement(cursor.getLong(0), cursor.getInt(1));
		}
	}

	private Element createElement(long id, int version)
	{
		Element element = mock(Element.class);
		when(element.getId()).thenReturn(id);
		when(element.getVersion()).thenReturn(version);
		return element;
	}
}
