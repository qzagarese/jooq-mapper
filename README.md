jooq-mapper
===========

A Sunday afternoon rant to support annotation based object relational mapping for jOOQ query results.
For now it supports only one-to-many and embedded entities, but I plan to add many-to-many mapping support, maybe.
Don't use this, unless you hate yourself. If you ended up here, maybe you should have used Hibernate.

It provides the following annotations:
- `JooqTable`: tells the mapper where to look inside your jOOQ generated classes.
- `JooqTableProperty`: maps a field to a jOOQ generated class property (uppercase of the table column name)
- `OneToOne`: maps a one-to-one relationship.
- `OneToMany`: well...it maps a one-to-many-relationship.
- `Embedded`: it groups some properties into a pojo, based on how you have used `JooqTableProperty` inside such pojo...I mean, just look at the example.

With reference to the usual jOOQ example database, you can annotate an entity as follows:

```java
import com.github.qzagarese.jooqmapper.annotations.JooqTable;
import com.github.qzagarese.jooqmapper.annotations.JooqTableProperty;
import com.github.qzagarese.jooqmapper.annotations.OneToMany;
import org.jooq.example.spring.db.tables.Author;

import java.util.Set;

@JooqTable(Author.class)
public class MyAuthor {

    public MyAuthor(){}

    @JooqTableProperty("ID")
    private Integer id;

    @JooqTableProperty("FIRST_NAME")
    private String firstName;

    @JooqTableProperty("LAST_NAME")
    private String lastName;

    @OneToMany(targetEntity = MyBook.class, otherPrimaryKeyColumn = "ID")
    private Set<MyBook> books;
}
```

```java
import com.github.qzagarese.jooqmapper.annotations.Embedded;
import com.github.qzagarese.jooqmapper.annotations.JooqTable;
import com.github.qzagarese.jooqmapper.annotations.JooqTableProperty;
import org.jooq.example.spring.db.tables.Book;

@JooqTable(Book.class)
public class MyBook {

    @JooqTableProperty("ID")
    private Integer id;

    @JooqTableProperty("TITLE")
    private String title;

    @Embedded
    private MyText text;

}
```
```java
import com.github.qzagarese.jooqmapper.annotations.JooqTableProperty;

public class MyText {

    public MyText(){}

    @JooqTableProperty("CONTENT_TEXT")
    private String text;

}
```

`Book.class` and `Author.class` are jOOQ auto generated entities.
If you have a query like the following:

```java
Result<Record> authorRecords = create.select()
				.from(Book.BOOK)
				.join(Author.AUTHOR)
				.on(AUTHOR.ID.eq(BOOK.AUTHOR_ID))
				.fetch();

```

Then you can map the result as follows;
```java
List<MyAuthor> authors = new JooqMapper<>(authorRecords.stream(), AUTHOR.ID)
				.buildStream(MyAuthor.class)
				.collect(Collectors.toList());
```

Where `AUTHOR.ID` is the reference to an auto generated jOOQ field that you are passing to index your result (pretty much it's your primary key).
That's all folks, because this should probably die here!


