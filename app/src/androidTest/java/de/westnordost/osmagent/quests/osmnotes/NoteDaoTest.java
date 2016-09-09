package de.westnordost.osmagent.quests.osmnotes;

import java.util.Date;
import java.util.ListIterator;

import de.westnordost.osmagent.quests.OsmagentDbTestCase;
import de.westnordost.osmapi.map.data.OsmLatLon;
import de.westnordost.osmapi.notes.Note;
import de.westnordost.osmapi.notes.NoteComment;
import de.westnordost.osmapi.user.User;

public class NoteDaoTest extends OsmagentDbTestCase
{
	private NoteDao dao;

	@Override public void setUp()
	{
		super.setUp();
		dao = new NoteDao(dbHelper, serializer);
	}

	public void testPutGetNoClosedDate()
	{
		Note note = createNote();

		dao.put(note);
		Note dbNote = dao.get(note.id);
		checkEqual(note, dbNote);
	}

	public void testPutReplace()
	{
		Note note = createNote();
		dao.put(note);
		note.status = Note.Status.CLOSED;
		dao.put(note);

		Note dbNote = dao.get(note.id);
		checkEqual(note, dbNote);
	}

	public void testPutGetWithClosedDate()
	{
		Note note = createNote();
		note.dateClosed = new Date(6000);

		dao.put(note);
		Note dbNote = dao.get(note.id);
		checkEqual(note, dbNote);
	}

	public void testDeleteUnreferenced()
	{
		Note note = createNote();
		dao.put(note);
		assertEquals(1,dao.deleteUnreferenced());

		dao.put(note);
		new OsmNoteQuestDao(dbHelper,serializer).add(new OsmNoteQuest(note));
		assertEquals(0,dao.deleteUnreferenced());
	}

	private void checkEqual(Note note, Note dbNote)
	{
		assertEquals(note.id, dbNote.id);
		assertEquals(note.position, dbNote.position);
		assertEquals(note.status, dbNote.status);
		assertEquals(note.dateCreated, dbNote.dateCreated);
		assertEquals(note.dateClosed, dbNote.dateClosed);

		assertEquals(note.comments.size(), dbNote.comments.size());
		ListIterator<NoteComment> it, dbIt;
		it = note.comments.listIterator();
		dbIt = dbNote.comments.listIterator();

		while(it.hasNext() && dbIt.hasNext())
		{
			NoteComment comment = it.next();
			NoteComment dbComment = dbIt.next();
			assertEquals(comment.action, dbComment.action);
			assertEquals(comment.date, dbComment.date);
			assertEquals(comment.text, dbComment.text);
			assertEquals(comment.user.displayName, dbComment.user.displayName);
			assertEquals(comment.user.id, dbComment.user.id);
		}
	}

	static Note createNote()
	{
		Note note = new Note();
		note.position = new OsmLatLon(1,1);
		note.status = Note.Status.OPEN;
		note.id = 5;
		note.dateCreated = new Date(5000);

		NoteComment comment = new NoteComment();
		comment.text = "hi";
		comment.date = new Date(5000);
		comment.action = NoteComment.Action.OPENED;
		comment.user = new User(5,"PingPong");
		note.comments.add(comment);

		return note;
	}
}
