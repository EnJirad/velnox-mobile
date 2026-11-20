# PART 02 — High-Performance Image Engine & RAM Optimization

## Context & Objectives
To ensure Velnox Mobile operates smoothly without lag or memory leaks on Android devices (including budget devices with limited RAM), the image loading pipeline must be configured with strict memory caps, disk caching, downsampling, and efficient bitmap management.

## Key Deliverables
1. **Application-Level ImageLoaderFactory:**
   - Configure `VelnoxApplication` to implement `ImageLoaderFactory`.
   - Set Coil `MemoryCache` with an explicit memory cap (max 15%–20% of device available heap) to avoid high memory pressure.
   - Configure LRU `DiskCache` (e.g. 50 MB) inside `context.cacheDir` for persistent offline caching of catalog thumbnails and banners.
   - Enable `crossfade(200)` transitions for smooth visual rendering without frame drops.
   - Set `respectCacheHeaders(false)` so images remain cached smoothly during the user session.
2. **Low-Memory Downsampling & Bitmap Pooling:**
   - Enable `allowRgb565(true)` when running on low-RAM devices or memory-constrained environments.
   - Ensure all image loading calls specify thumbnail dimensions or `ContentScale.Crop` with proper clipping to avoid allocating full-resolution bitmaps in RAM.
3. **Lazy List Image Recycling:**
   - Verify that all `LazyColumn` and `LazyRow` product feeds have stable, unique `key` parameters so Coil image requests are properly recycled and cancelled when items scroll off-screen.
4. **Verification & Tests:**
   - Verify application startup initializes the custom `ImageLoader` without exceptions.
   - Run unit tests and verify CI build pass on GitHub Actions.
