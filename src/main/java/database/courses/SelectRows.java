package database.courses;

import static database.generated.Tables.*;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.groupConcat;

import database.models.FullRow;
import database.models.Row;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import nyu.Meeting;
import nyu.SubjectCode;
import org.jooq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelectRows {

  private static Logger logger =
      LoggerFactory.getLogger("database.courses.SelectCourseSectionRows");

  public static Stream<Row> selectRows(DSLContext context, int epoch,
                                       SubjectCode code) {
    return selectRows(context, COURSES.EPOCH.eq(epoch),
                      COURSES.SCHOOL.eq(code.school),
                      COURSES.SUBJECT.eq(code.code));
  }
  public static Stream<Row> selectRows(DSLContext context,
                                       Condition... conditions) {
    long start = System.nanoTime();
    Result<Record> records =
        context
            .select(COURSES.asterisk(), SECTIONS.ID,
                    SECTIONS.REGISTRATION_NUMBER, SECTIONS.SECTION_CODE,
                    SECTIONS.SECTION_TYPE, SECTIONS.SECTION_STATUS,
                    SECTIONS.ASSOCIATED_WITH, SECTIONS.WAITLIST_TOTAL,
                    SECTIONS.NAME, SECTIONS.MIN_UNITS, SECTIONS.MAX_UNITS,
                    SECTIONS.LOCATION,
                    groupConcat(
                        coalesce(IS_TEACHING_SECTION.INSTRUCTOR_NAME, ""), ";")
                        .as("section_instructors"),
                    groupConcat(MEETINGS.BEGIN_DATE, ";").as("begin_dates"),
                    groupConcat(MEETINGS.DURATION, ";").as("durations"),
                    groupConcat(MEETINGS.END_DATE, ";").as("end_dates"))
            .from(COURSES)
            .leftJoin(SECTIONS)
            .on(SECTIONS.COURSE_ID.eq(COURSES.ID))
            .leftJoin(IS_TEACHING_SECTION)
            .on(SECTIONS.ID.eq(IS_TEACHING_SECTION.SECTION_ID))
            .leftJoin(MEETINGS)
            .on(SECTIONS.ID.eq(MEETINGS.SECTION_ID))
            .where(conditions)
            .groupBy(SECTIONS.ID)
            .fetch();
    long end = System.nanoTime();
    logger.info((end - start) / 1000000 + " milliseconds for database");

    return StreamSupport
        .stream(records.spliterator(),
                false) // @Performance Should this be true?
        .map(r -> new Row(r));
  }
}
