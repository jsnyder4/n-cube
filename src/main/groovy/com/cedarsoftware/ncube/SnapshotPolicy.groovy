package com.cedarsoftware.ncube

import groovy.transform.CompileStatic

/**
 * Snapshot policies permitted by NCube file cache
 */
@CompileStatic
enum SnapshotPolicy
{
    RELEASE_ONLY,   // prevent caching of snapshots; only cache release versions (default)
    OFFLINE,        // only use cubes found in file cache; exceptions are thrown for missing cubes
    UPDATE,         // snapshot cubes are only loaded if not found in the file cache; manual cleanup of cache required
    FORCE           // sha1 check is performed before loading cubes from file cache to ensure latest snapshot is used
}