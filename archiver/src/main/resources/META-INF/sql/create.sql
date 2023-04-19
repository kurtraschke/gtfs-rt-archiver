CREATE TABLE IF NOT EXISTS feed_contents
(
    producer             text        NOT NULL,
    feed                 text        NOT NULL,
    fetch_time           timestamptz NOT NULL,
    is_error             boolean     NOT NULL,
    error_message        text,
    response_time_millis int4,
    status_code          int4,
    status_message       text,
    protocol             text,
    response_headers     jsonb,
    response_body        bytea,
    response_body_length int4,
    response_contents    jsonb,
    PRIMARY KEY (producer, feed, fetch_time)
);

CREATE OR REPLACE VIEW v_latest_update AS
SELECT producer, feed, MAX(fetch_time) fetch_time
FROM feed_contents
WHERE NOT is_error
GROUP BY producer, feed;

CREATE OR REPLACE VIEW v_feed_stats
            (producer, feed, fetch_time, header_date, last_modified, etag, gtfs_rt_header_timestamp) AS
SELECT t1.producer,
       t1.feed,
       t1.fetch_time,
       TO_TIMESTAMP(jsonb_path_query_first(t1.response_headers, '$."date"[0]'::jsonpath) #>> '{}'::text[],
                    'Dy, DD Mon YYYY HH24:MI:SS GMT'::text)                                                AS header_date,
       TO_TIMESTAMP(jsonb_path_query_first(t1.response_headers, '$."last-modified"[0]'::jsonpath) #>> '{}'::text[],
                    'Dy, DD Mon YYYY HH24:MI:SS GMT'::text)                                                AS last_modified,
       jsonb_path_query_first(t1.response_headers, '$."etag"[0]'::jsonpath) #>> '{}'::text[]               AS etag,
       TO_TIMESTAMP(jsonb_path_query_first(t1.response_contents,
                                           '$."header"."timestamp".double()'::jsonpath)::double precision) AS gtfs_rt_header_timestamp
FROM feed_contents t1
WHERE (t1.producer, t1.feed, t1.fetch_time) IN (SELECT producer, feed, fetch_time FROM v_latest_update)
ORDER BY t1.producer, t1.feed;
