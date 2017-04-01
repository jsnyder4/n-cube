DROP TABLE if exists n_cube;
CREATE TABLE n_cube (
  n_cube_id bigint NOT NULL,
  n_cube_nm VARCHAR(250) NOT NULL,
  tenant_cd VARCHAR(10) DEFAULT 'NONE',
  cube_value_bin varbinary(999999),
  create_dt TIMESTAMP NOT NULL,
  create_hid VARCHAR(20),
  version_no_cd VARCHAR(16) DEFAULT '0.1.0' NOT NULL,
  status_cd VARCHAR(16) DEFAULT 'SNAPSHOT' NOT NULL,
  app_cd VARCHAR(20) NOT NULL,
  test_data_bin varbinary(999999),
  notes_bin varbinary(999999),
  revision_number bigint DEFAULT '0' NOT NULL,
  branch_id VARCHAR(80) DEFAULT 'HEAD' NOT NULL,
  sha1 varchar(40) DEFAULT NULL,
  head_sha1 varchar(40) DEFAULT NULL,
  changed int DEFAULT NULL,
  UNIQUE (n_cube_nm, tenant_cd, app_cd, version_no_cd, branch_id, revision_number)
);