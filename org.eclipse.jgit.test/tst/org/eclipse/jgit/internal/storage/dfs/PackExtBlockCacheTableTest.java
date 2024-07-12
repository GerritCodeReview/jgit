/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.Ref;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.RefLoader;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DfsBlockCachePackExtConfig;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.DfsBlockCacheStats;
import org.eclipse.jgit.internal.storage.dfs.PackExtBlockCacheTable.PackExtsCacheTablePair;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;
import org.mockito.Mockito;

public class PackExtBlockCacheTableTest {
	@Test
	public void fromBlockCacheConfigs_createsDfsPackExtBlockCacheTables() {
		DfsBlockCacheConfig cacheConfig = new DfsBlockCacheConfig();
		cacheConfig.setPackExtCacheConfigurations(
				List.of(new DfsBlockCachePackExtConfig(EnumSet.of(PackExt.PACK),
						new DfsBlockCacheConfig())));
		assertNotNull(
				PackExtBlockCacheTable.fromBlockCacheConfigs(cacheConfig));
	}

	@Test
	public void fromBlockCacheConfigs_noPackExtConfigurationGiven_packExtCacheConfigurationsIsEmpty_throws() {
		DfsBlockCacheConfig config = new DfsBlockCacheConfig();
		config.setPackExtCacheConfigurations(List.of());
		assertThrows(IllegalArgumentException.class,
				() -> PackExtBlockCacheTable.fromBlockCacheConfigs(config));
	}

	@Test
	public void fromCacheTables_hasDuplicatePackExts_throws() {
		assertThrows(IllegalArgumentException.class,
				() -> PackExtBlockCacheTable.fromCacheTables(
						mock(DfsBlockCacheTable.class),
						List.of(new PackExtsCacheTablePair(Set.of(PackExt.PACK),
								mock(DfsBlockCacheTable.class)),
								new PackExtsCacheTablePair(Set.of(PackExt.PACK),
										mock(DfsBlockCacheTable.class)))));
	}

	@Test
	public void fromCacheTables_hasDuplicateCacheTables_throws() {
		DfsBlockCacheTable cacheTable = mock(DfsBlockCacheTable.class);
		assertThrows(IllegalArgumentException.class,
				() -> PackExtBlockCacheTable.fromCacheTables(
						mock(DfsBlockCacheTable.class),
						List.of(new PackExtsCacheTablePair(Set.of(PackExt.PACK),
								cacheTable),
								new PackExtsCacheTablePair(
										Set.of(PackExt.INDEX), cacheTable))));
	}

	@Test
	public void hasBlock0_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey streamKey = new TestKey(PackExt.BITMAP_INDEX);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.hasBlock0(any(DfsStreamKey.class)))
				.thenReturn(false);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.hasBlock0(any(DfsStreamKey.class)))
				.thenReturn(true);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertTrue(tables.hasBlock0(streamKey));
	}

	@Test
	public void hasBlock0_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey streamKey = new TestKey(PackExt.PACK);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.hasBlock0(any(DfsStreamKey.class)))
				.thenReturn(true);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.hasBlock0(any(DfsStreamKey.class)))
				.thenReturn(false);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertTrue(tables.hasBlock0(streamKey));
	}

	@Test
	public void getOrLoad_packExtMapsToCacheTable_callsBitmapIndexCacheTable()
			throws Exception {
		BlockBasedFile blockBasedFile = new BlockBasedFile(null,
				mock(DfsPackDescription.class), PackExt.BITMAP_INDEX) {
		};
		DfsBlock dfsBlock = mock(DfsBlock.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.getOrLoad(any(BlockBasedFile.class),
				anyLong(), any(DfsReader.class),
				any(DfsBlockCache.ReadableChannelSupplier.class)))
				.thenReturn(mock(DfsBlock.class));
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.getOrLoad(any(BlockBasedFile.class),
				anyLong(), any(DfsReader.class),
				any(DfsBlockCache.ReadableChannelSupplier.class)))
				.thenReturn(dfsBlock);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertThat(
				tables.getOrLoad(blockBasedFile, 0, mock(DfsReader.class),
						mock(DfsBlockCache.ReadableChannelSupplier.class)),
				sameInstance(dfsBlock));
	}

	@Test
	public void getOrLoad_packExtDoesNotMapToCacheTable_callsDefaultCache()
			throws Exception {
		BlockBasedFile blockBasedFile = new BlockBasedFile(null,
				mock(DfsPackDescription.class), PackExt.PACK) {
		};
		DfsBlock dfsBlock = mock(DfsBlock.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.getOrLoad(any(BlockBasedFile.class),
				anyLong(), any(DfsReader.class),
				any(DfsBlockCache.ReadableChannelSupplier.class)))
				.thenReturn(dfsBlock);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.getOrLoad(any(BlockBasedFile.class),
				anyLong(), any(DfsReader.class),
				any(DfsBlockCache.ReadableChannelSupplier.class)))
				.thenReturn(mock(DfsBlock.class));

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertThat(
				tables.getOrLoad(blockBasedFile, 0, mock(DfsReader.class),
						mock(DfsBlockCache.ReadableChannelSupplier.class)),
				sameInstance(dfsBlock));
	}

	@Test
	public void getOrLoadRef_packExtMapsToCacheTable_callsBitmapIndexCacheTable()
			throws Exception {
		Ref<Integer> ref = mock(Ref.class);
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.BITMAP_INDEX);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.getOrLoadRef(any(DfsStreamKey.class),
				anyLong(), any(RefLoader.class))).thenReturn(mock(Ref.class));
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.getOrLoadRef(any(DfsStreamKey.class),
				anyLong(), any(RefLoader.class))).thenReturn(ref);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertThat(tables.getOrLoadRef(dfsStreamKey, 0, mock(RefLoader.class)),
				sameInstance(ref));
	}

	@Test
	public void getOrLoadRef_packExtDoesNotMapToCacheTable_callsDefaultCache()
			throws Exception {
		Ref<Integer> ref = mock(Ref.class);
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.PACK);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.getOrLoadRef(any(DfsStreamKey.class),
				anyLong(), any(RefLoader.class))).thenReturn(ref);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.getOrLoadRef(any(DfsStreamKey.class),
				anyLong(), any(RefLoader.class))).thenReturn(mock(Ref.class));

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertThat(tables.getOrLoadRef(dfsStreamKey, 0, mock(RefLoader.class)),
				sameInstance(ref));
	}

	@Test
	public void putDfsBlock_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.BITMAP_INDEX);
		DfsBlock dfsBlock = new DfsBlock(dfsStreamKey, 0, new byte[0]);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		tables.put(dfsBlock);
		Mockito.verify(bitmapIndexCacheTable, times(1)).put(dfsBlock);
	}

	@Test
	public void putDfsBlock_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.PACK);
		DfsBlock dfsBlock = new DfsBlock(dfsStreamKey, 0, new byte[0]);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		tables.put(dfsBlock);
		Mockito.verify(defaultBlockCacheTable, times(1)).put(dfsBlock);
	}

	@Test
	public void putDfsStreamKey_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.BITMAP_INDEX);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.put(any(DfsStreamKey.class), anyLong(),
				anyLong(), anyInt())).thenReturn(mock(Ref.class));
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.put(any(DfsStreamKey.class), anyLong(),
				anyLong(), anyInt())).thenReturn(ref);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertThat(tables.put(dfsStreamKey, 0, 0, 0), sameInstance(ref));
	}

	@Test
	public void putDfsStreamKey_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.PACK);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.put(any(DfsStreamKey.class), anyLong(),
				anyLong(), anyInt())).thenReturn(ref);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.put(any(DfsStreamKey.class), anyLong(),
				anyLong(), anyInt())).thenReturn(mock(Ref.class));

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertThat(tables.put(dfsStreamKey, 0, 0, 0), sameInstance(ref));
	}

	@Test
	public void putRef_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.BITMAP_INDEX);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.putRef(any(DfsStreamKey.class), anyLong(),
				anyInt())).thenReturn(mock(Ref.class));
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.putRef(any(DfsStreamKey.class), anyLong(),
				anyInt())).thenReturn(ref);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertThat(tables.putRef(dfsStreamKey, 0, 0), sameInstance(ref));
	}

	@Test
	public void putRef_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.PACK);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.putRef(any(DfsStreamKey.class), anyLong(),
				anyInt())).thenReturn(ref);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.putRef(any(DfsStreamKey.class), anyLong(),
				anyInt())).thenReturn(mock(Ref.class));

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertThat(tables.putRef(dfsStreamKey, 0, 0), sameInstance(ref));
	}

	@Test
	public void contains_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey streamKey = new TestKey(PackExt.BITMAP_INDEX);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.contains(any(DfsStreamKey.class),
				anyLong())).thenReturn(false);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.contains(any(DfsStreamKey.class), anyLong()))
				.thenReturn(true);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertTrue(tables.contains(streamKey, 0));
	}

	@Test
	public void contains_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey streamKey = new TestKey(PackExt.PACK);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.contains(any(DfsStreamKey.class),
				anyLong())).thenReturn(true);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.contains(any(DfsStreamKey.class), anyLong()))
				.thenReturn(false);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertTrue(tables.contains(streamKey, 0));
	}

	@Test
	public void get_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.BITMAP_INDEX);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.get(any(DfsStreamKey.class), anyLong()))
				.thenReturn(mock(Ref.class));
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.get(any(DfsStreamKey.class), anyLong()))
				.thenReturn(ref);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertThat(tables.get(dfsStreamKey, 0), sameInstance(ref));
	}

	@Test
	public void get_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.PACK);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.get(any(DfsStreamKey.class), anyLong()))
				.thenReturn(ref);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.get(any(DfsStreamKey.class), anyLong()))
				.thenReturn(mock(Ref.class));

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX), bitmapIndexCacheTable)));

		assertThat(tables.get(dfsStreamKey, 0), sameInstance(ref));
	}

	@Test
	public void getBlockCacheStatsGetCurrentSizeConsolidatesAllTableCurrentSizes() {
		long[] currentSizes = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		packStats.addToLiveBytes(new TestKey(PackExt.PACK), 5);
		currentSizes[PackExt.PACK.getPosition()] = 5;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		bitmapStats.addToLiveBytes(new TestKey(PackExt.BITMAP_INDEX), 6);
		currentSizes[PackExt.BITMAP_INDEX.getPosition()] = 6;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		indexStats.addToLiveBytes(new TestKey(PackExt.INDEX), 7);
		currentSizes[PackExt.INDEX.getPosition()] = 7;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				cacheTableWithStats(packStats),
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX),
						cacheTableWithStats(bitmapStats)),
						new PackExtBlockCacheTable.PackExtsCacheTablePair(
								Set.of(PackExt.INDEX),
								cacheTableWithStats(indexStats))));

		assertArrayEquals(tables.getBlockCacheStats().getCurrentSize(),
				currentSizes);
	}

	@Test
	public void getBlockCacheStatsGetHitCountConsolidatesAllTableHitCounts() {
		long[] hitCounts = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementCounter(5,
				() -> packStats.incrementHit(new TestKey(PackExt.PACK)));
		hitCounts[PackExt.PACK.getPosition()] = 5;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementCounter(6, () -> bitmapStats
				.incrementHit(new TestKey(PackExt.BITMAP_INDEX)));
		hitCounts[PackExt.BITMAP_INDEX.getPosition()] = 6;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementCounter(7,
				() -> indexStats.incrementHit(new TestKey(PackExt.INDEX)));
		hitCounts[PackExt.INDEX.getPosition()] = 7;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				cacheTableWithStats(packStats),
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX),
						cacheTableWithStats(bitmapStats)),
						new PackExtBlockCacheTable.PackExtsCacheTablePair(
								Set.of(PackExt.INDEX),
								cacheTableWithStats(indexStats))));

		assertArrayEquals(tables.getBlockCacheStats().getHitCount(), hitCounts);
	}

	@Test
	public void getBlockCacheStatsGetMissCountConsolidatesAllTableMissCounts() {
		long[] missCounts = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementCounter(5,
				() -> packStats.incrementMiss(new TestKey(PackExt.PACK)));
		missCounts[PackExt.PACK.getPosition()] = 5;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementCounter(6, () -> bitmapStats
				.incrementMiss(new TestKey(PackExt.BITMAP_INDEX)));
		missCounts[PackExt.BITMAP_INDEX.getPosition()] = 6;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementCounter(7,
				() -> indexStats.incrementMiss(new TestKey(PackExt.INDEX)));
		missCounts[PackExt.INDEX.getPosition()] = 7;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				cacheTableWithStats(packStats),
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX),
						cacheTableWithStats(bitmapStats)),
						new PackExtBlockCacheTable.PackExtsCacheTablePair(
								Set.of(PackExt.INDEX),
								cacheTableWithStats(indexStats))));

		assertArrayEquals(tables.getBlockCacheStats().getMissCount(),
				missCounts);
	}

	@Test
	public void getBlockCacheStatsGetTotalRequestCountConsolidatesAllTableTotalRequestCounts() {
		long[] totalRequestCounts = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementCounter(5, () -> {
			packStats.incrementHit(new TestKey(PackExt.PACK));
			packStats.incrementMiss(new TestKey(PackExt.PACK));
		});
		totalRequestCounts[PackExt.PACK.getPosition()] = 10;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementCounter(6, () -> {
			bitmapStats.incrementHit(new TestKey(PackExt.BITMAP_INDEX));
			bitmapStats.incrementMiss(new TestKey(PackExt.BITMAP_INDEX));
		});
		totalRequestCounts[PackExt.BITMAP_INDEX.getPosition()] = 12;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementCounter(7, () -> {
			indexStats.incrementHit(new TestKey(PackExt.INDEX));
			indexStats.incrementMiss(new TestKey(PackExt.INDEX));
		});
		totalRequestCounts[PackExt.INDEX.getPosition()] = 14;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				cacheTableWithStats(packStats),
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX),
						cacheTableWithStats(bitmapStats)),
						new PackExtBlockCacheTable.PackExtsCacheTablePair(
								Set.of(PackExt.INDEX),
								cacheTableWithStats(indexStats))));

		assertArrayEquals(tables.getBlockCacheStats().getTotalRequestCount(),
				totalRequestCounts);
	}

	@Test
	public void getBlockCacheStatsGetHitRatioConsolidatesAllTableHitRatios() {
		long[] hitRatios = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementCounter(5,
				() -> packStats.incrementHit(new TestKey(PackExt.PACK)));
		hitRatios[PackExt.PACK.getPosition()] = 100;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementCounter(6, () -> {
			bitmapStats.incrementHit(new TestKey(PackExt.BITMAP_INDEX));
			bitmapStats.incrementMiss(new TestKey(PackExt.BITMAP_INDEX));
		});
		hitRatios[PackExt.BITMAP_INDEX.getPosition()] = 50;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementCounter(7,
				() -> indexStats.incrementMiss(new TestKey(PackExt.INDEX)));
		hitRatios[PackExt.INDEX.getPosition()] = 0;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				cacheTableWithStats(packStats),
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX),
						cacheTableWithStats(bitmapStats)),
						new PackExtBlockCacheTable.PackExtsCacheTablePair(
								Set.of(PackExt.INDEX),
								cacheTableWithStats(indexStats))));

		assertArrayEquals(tables.getBlockCacheStats().getHitRatio(), hitRatios);
	}

	@Test
	public void getBlockCacheStatsGetEvictionsConsolidatesAllTableEvictions() {
		long[] evictions = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementCounter(5,
				() -> packStats.incrementEvict(new TestKey(PackExt.PACK)));
		evictions[PackExt.PACK.getPosition()] = 5;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementCounter(6, () -> bitmapStats
				.incrementEvict(new TestKey(PackExt.BITMAP_INDEX)));
		evictions[PackExt.BITMAP_INDEX.getPosition()] = 6;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementCounter(7,
				() -> indexStats.incrementEvict(new TestKey(PackExt.INDEX)));
		evictions[PackExt.INDEX.getPosition()] = 7;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				cacheTableWithStats(packStats),
				List.of(new PackExtBlockCacheTable.PackExtsCacheTablePair(
						Set.of(PackExt.BITMAP_INDEX),
						cacheTableWithStats(bitmapStats)),
						new PackExtBlockCacheTable.PackExtsCacheTablePair(
								Set.of(PackExt.INDEX),
								cacheTableWithStats(indexStats))));

		assertArrayEquals(tables.getBlockCacheStats().getEvictions(),
				evictions);
	}

	private static void incrementCounter(int amount, Runnable fn) {
		for (int i = 0; i < amount; i++) {
			fn.run();
		}
	}

	private static long[] createEmptyStatsArray() {
		return new long[PackExt.values().length];
	}

	private static DfsBlockCacheTable cacheTableWithStats(
			DfsBlockCacheStats dfsBlockCacheStats) {
		DfsBlockCacheTable cacheTable = mock(DfsBlockCacheTable.class);
		when(cacheTable.getBlockCacheStats()).thenReturn(dfsBlockCacheStats);
		return cacheTable;
	}

	private static class TestKey extends DfsStreamKey {
		TestKey(PackExt packExt) {
			super(0, packExt);
		}

		@Override
		public boolean equals(Object o) {
			return false;
		}
	}
}
