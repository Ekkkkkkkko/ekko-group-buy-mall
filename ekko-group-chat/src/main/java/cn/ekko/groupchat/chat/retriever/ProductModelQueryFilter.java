package cn.ekko.groupchat.chat.retriever;

import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/** 从问题中识别 TP-LINK 型号，并生成 ES productModel 精确过滤条件。 */
@Component
public class ProductModelQueryFilter {

    private static final Pattern PRODUCT_MODEL = Pattern.compile(
            "(?i)(?<![A-Z0-9])((?:TL|ARCHER|DECO)-[A-Z0-9][A-Z0-9-]*)(?![A-Z0-9-])"
    );

    public Filter filter(Query query) {
        return detect(query.text())
                .<Filter>map(model -> metadataKey("productModel").isEqualTo(model))
                .orElse(null);
    }

    public Optional<String> detect(String question) {
        if (question == null) {
            return Optional.empty();
        }
        Matcher matcher = PRODUCT_MODEL.matcher(question);
        return matcher.find()
                ? Optional.of(matcher.group(1).toUpperCase(Locale.ROOT))
                : Optional.empty();
    }
}
