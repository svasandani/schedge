package database.courses;

import static database.generated.Tables.*;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.groupConcat;

import database.models.FullRow;
import database.models.Row;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import nyu.Meeting;
import nyu.SubjectCode;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Utils;

public final class SearchRows {

  private static Logger logger =
      LoggerFactory.getLogger("database.courses.SearchCourses");

  public static Stream<Row>
  searchRows(Connection conn, int epoch, String subject, String school,
             int resultSize, String query, int titleWeight,
             int descriptionWeight, int notesWeight, int prereqsWeight)
      throws SQLException {
    if (resultSize <= 0) {
      throw new IllegalArgumentException("result size must be positive");
    } else if (resultSize > 50)
      resultSize = 50;

    if (titleWeight == 0 && descriptionWeight == 0 && notesWeight == 0 &&
        prereqsWeight == 0) {
      throw new IllegalArgumentException("all of the weights were zero");
    }

    ArrayList<String> fields = new ArrayList<>();
    ArrayList<String> rankings = new ArrayList<>();
    if (titleWeight != 0) {
      fields.add("courses.name_vec @@ q.query");
      fields.add("sections.name_vec @@ q.query");
      rankings.add(titleWeight + " * ts_rank_cd(courses.name_vec, q.query)");
      rankings.add(titleWeight + " * ts_rank_cd(sections.name_vec, q.query)");
    }
    if (descriptionWeight != 0) {
      fields.add("courses.description_vec @@ q.query");
      rankings.add(descriptionWeight +
                   " * ts_rank_cd(courses.description_vec, q.query)");
    }
    if (notesWeight != 0) {
      fields.add("sections.notes_vec @@ q.query");
      rankings.add(notesWeight + " * ts_rank_cd(sections.notes_vec, q.query)");
    }
    if (prereqsWeight != 0) {
      fields.add("sections.prereqs_vec @@ q.query");
      rankings.add(prereqsWeight +
                   " * ts_rank_cd(sections.prereqs_vec, q.query)");
    }

    if (subject != null)
      subject = subject.toUpperCase();
    if (school != null)
      school = school.toUpperCase();
    String begin = "WITH q (query) AS (SELECT plainto_tsquery(?)) "
                   + "SELECT DISTINCT courses.id FROM q, "
                   + "courses JOIN sections ON courses.id = sections.course_id "
                   + "WHERE (" + String.join(" OR ", fields) + ") AND ";
    PreparedStatement idStmt;
    if (subject != null && school != null) {
      idStmt = conn.prepareStatement(
          begin +
          "epoch = ? AND courses.subject = ? AND courses.school = ? LIMIT ?");
      Utils.setArray(idStmt,
                     new Object[] {query, epoch, subject, school, resultSize});
    } else if (subject != null) {
      idStmt = conn.prepareStatement(
          begin + "epoch = ? AND courses.subject = ? LIMIT ?");
      Utils.setArray(idStmt, new Object[] {query, epoch, subject, resultSize});
    } else if (school != null) {
      idStmt =

          conn.prepareStatement(begin +
                                "epoch = ? AND courses.school = ? LIMIT ?");
      Utils.setArray(idStmt, new Object[] {query, epoch, school});
    } else {
      idStmt = conn.prepareStatement(begin + "epoch = ?");
      Utils.setArray(idStmt, new Object[] {query, epoch, resultSize});
    }

    ArrayList<Integer> result = new ArrayList<>();
    ResultSet rs = idStmt.executeQuery();
    while (rs.next()) {
      result.add(rs.getInt(1));
    }
    rs.close();

    PreparedStatement rowStmt = conn.prepareStatement(
        "WITH q (query) AS (SELECT plainto_tsquery(?)) "
        + "SELECT courses.*, sections.id AS section_id, "
        + "sections.registration_number, sections.section_code, "
        + "sections.section_type, sections.section_status, "
        + "sections.associated_with, sections.waitlist_total, "
        + "sections.name AS section_name, "
        + "sections.min_units, sections.max_units, sections.location, "
        + "array_to_string(array_agg(is_teaching_section.instructor_name),';') "
        + "AS section_instructors "
        + "FROM q, courses LEFT JOIN sections "
        + "ON courses.id = sections.course_id "
        + "LEFT JOIN is_teaching_section "
        + "ON sections.id = is_teaching_section.section_id "
        + "WHERE courses.id = ANY (?) "
        + "GROUP BY q.query, courses.id, sections.id "
        + "ORDER BY " + String.join(" + ", rankings) + " DESC");
    Utils.setArray(rowStmt, query,
                   conn.createArrayOf("INTEGER", result.toArray()));
    Condition condition = COURSES.ID.in(result);
    Map<Integer, List<Meeting>> meetingsList =
        selectMeetings(conn, " courses.id = ANY (?) ",
                       conn.createArrayOf("integer", result.toArray()));

    ArrayList<Row> rows = new ArrayList<>();
    rs = rowStmt.executeQuery();
    while (rs.next()) {
      rows.add(new Row(rs, meetingsList.get(rs.getInt("section_id"))));
    }

    rs.close();
    return rows.stream();
  }

  public static Stream<FullRow>
  searchFullRows(DSLContext context, int epoch, String subject, String school,
                 int resultSize, String query, int titleWeight,
                 int descriptionWeight, int notesWeight, int prereqsWeight) {
    if (resultSize <= 0) {
      throw new IllegalArgumentException("result size must be positive");
    } else if (resultSize > 50)
      resultSize = 50;

    if (titleWeight == 0 && descriptionWeight == 0 && notesWeight == 0 &&
        prereqsWeight == 0) {
      throw new IllegalArgumentException("all of the weights were zero");
    }
    CommonTableExpression<Record1<Object>> with =
        DSL.name("q").fields("query").as(
            DSL.select(DSL.field("plainto_tsquery(?)", query)));

    ArrayList<String> fields = new ArrayList<>();
    ArrayList<String> rankings = new ArrayList<>();
    if (titleWeight != 0) {
      fields.add("courses.name_vec @@ q.query");
      fields.add("sections.name_vec @@ q.query");
      rankings.add(titleWeight + " * ts_rank_cd(courses.name_vec, q.query)");
      rankings.add(titleWeight + " * ts_rank_cd(sections.name_vec, q.query)");
    }
    if (descriptionWeight != 0) {
      fields.add("courses.description_vec @@ q.query");
      rankings.add(descriptionWeight +
                   " * ts_rank_cd(courses.description_vec, q.query)");
    }
    if (notesWeight != 0) {
      fields.add("sections.notes_vec @@ q.query");
      rankings.add(notesWeight + " * ts_rank_cd(sections.notes_vec, q.query)");
    }
    if (prereqsWeight != 0) {
      fields.add("sections.prereqs_vec @@ q.query");
      rankings.add(prereqsWeight +
                   " * ts_rank_cd(sections.prereqs_vec, q.query)");
    }

    if (subject != null)
      subject = subject.toUpperCase();
    if (school != null)
      school = school.toUpperCase();
    String conditionString;
    Object[] objArray;
    if (subject != null && school != null) {
      conditionString =
          ") AND courses.subject = ? AND courses.school = ? AND courses.epoch = ?";
      objArray = new Object[] {subject, school, epoch};
    } else if (subject != null) {
      conditionString = ") AND courses.subject = ? AND courses.epoch = ?";
      objArray = new Object[] {subject, epoch};
    } else if (school != null) {
      conditionString = ") AND courses.school = ? AND courses.epoch = ?";
      objArray = new Object[] {school, epoch};
    } else {
      conditionString = ") AND courses.epoch = ?";
      objArray = new Object[] {epoch};
    }

    List<Integer> result =
        context.with(with)
            .selectDistinct(COURSES.ID)
            .from(DSL.table("q"), SECTIONS)
            .join(COURSES)
            .on(COURSES.ID.eq(SECTIONS.COURSE_ID))
            .where('(' + String.join(" OR ", fields) + conditionString,
                   objArray)
            .limit(resultSize)
            .fetch()
            .getValues(SECTIONS.ID);

    // Condition[] conditions =
    //     new Condition[] {COURSES.EPOCH.eq(epoch), COURSES.ID.in(result)};

    Condition condition = COURSES.ID.in(result);
    Map<Integer, List<Meeting>> meetingsList =
        SelectRows.selectMeetings(context, condition);
    Result<org.jooq.Record> records =
        context.with(with)
            .select(COURSES.asterisk(), SECTIONS.asterisk(),
                    groupConcat(
                        coalesce(IS_TEACHING_SECTION.INSTRUCTOR_NAME, ""), ";")
                        .as("section_instructors"))
            .from(DSL.table("q"), COURSES)
            .leftJoin(SECTIONS)
            .on(SECTIONS.COURSE_ID.eq(COURSES.ID))
            .leftJoin(IS_TEACHING_SECTION)
            .on(SECTIONS.ID.eq(IS_TEACHING_SECTION.SECTION_ID))
            .where(condition)
            .groupBy(DSL.field("q.query"), COURSES.ID, SECTIONS.ID)
            .orderBy(DSL.field(String.join(" + ", rankings) + " DESC"))
            .fetch();

    return StreamSupport
        .stream(records.spliterator(),
                false) // @Performance Should this be true?
        .map(r -> new FullRow(r, meetingsList.get(r.get(SECTIONS.ID))));
  }

  public static Map<Integer, List<Meeting>>
  selectMeetings(Connection conn, String conditions, Object... objects)
      throws SQLException {
    PreparedStatement stmt = conn.prepareStatement(
        "SELECT sections.id as section_id, "
        + "array_to_string(array_agg(meetings.begin_date),';'), "
        + "array_to_string(array_agg(meetings.duration),';'), "
        + "array_to_string(array_agg(meetings.end_date),';') "
        + "FROM courses JOIN sections ON courses.id = sections.course_id "
        + "JOIN meetings ON sections.id = meetings.section_id "
        + "WHERE " + conditions + " GROUP BY sections.id");
    Utils.setArray(stmt, objects);

    ResultSet rs = stmt.executeQuery();
    HashMap<Integer, List<Meeting>> meetings = new HashMap<>();
    while (rs.next()) {
      meetings.put(rs.getInt("section_id"),
                   SelectRows.meetingList(rs.getString(2), rs.getString(3),
                                          rs.getString(4)));
    }
    rs.close();
    return meetings;
  }
}
