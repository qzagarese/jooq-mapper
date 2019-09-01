jooq-mapper
===========

A Sunday afternoon rant to support annotation based object relational mapping for jOOQ query results.
For now it supports only flat entities which makes it pretty useless, but I plan to add one-to-many and many-to-many mapping support.
Don't use this, unless you hate yourself.

It has two annotations:
- `JooqTable`: tells the mapper where to look inside your jOOQ generated classes.
- `JooqTableProperty`: maps a field to a jOOQ generated class property (uppercase of the table column name)

With reference to the usual jOOQ example database, you can annotate an entity as follows:

```java
import com.github.qzagarese.jooqmapper.annotations.JooqTable;
import com.github.qzagarese.jooqmapper.annotations.JooqTableProperty;
import org.jooq.example.spring.db.tables.Book;

@JooqTable(Book.class)
public class MyBook {

    public MyBook(){}

    @JooqTableProperty("ID")
    private Integer id;

    @JooqTableProperty("TITLE")
    private String title;

    @JooqTableProperty("PUBLISHED_IN")
    private int published;

}
```

Where `Book.class` is the jOOQ auto generated entity.
If you have a query like the following:

```java
Result<Record> result = create.select()
				.from(BOOK)
				.fetch();
```

Then you can map the result as follows;
```java
List<MyBook> books = new JooqMapper<>(result, BOOK.ID)
    .buildStream(MyBook.class)
    .collect(Collectors.toList());
```

Where `BOOK.ID` is the reference to an auto generated jOOQ field that you are passing to index your result (pretty much it's your primary key).
That's all folks, because this should probably die here!


