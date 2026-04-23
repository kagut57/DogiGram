package org.telegram.ui.Feed;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Components.RecyclerListView;

public class FeedAutoplayManager extends RecyclerView.OnScrollListener {

    private final RecyclerListView listView;
    private final LinearLayoutManager layoutManager;
    private FeedPostCell currentPlayingCell = null;

    public FeedAutoplayManager(RecyclerListView listView, LinearLayoutManager layoutManager) {
        this.listView = listView;
        this.layoutManager = layoutManager;
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        super.onScrollStateChanged(recyclerView, newState);
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            checkAutoplay();
        }
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
    }

    public void checkAutoplay() {
        if (layoutManager == null || listView == null) return;

        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();

        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) return;

        FeedPostCell bestCandidate = null;
        int maxVisibleArea = 0;

        for (int i = firstVisible; i <= lastVisible; i++) {
            View view = layoutManager.findViewByPosition(i);
            if (view instanceof FeedPostCell) {
                FeedPostCell cell = (FeedPostCell) view;
                int visibleArea = getVisibleArea(cell);
                if (visibleArea > maxVisibleArea && visibleArea > cell.getHeight() * 0.6f) {
                    maxVisibleArea = visibleArea;
                    bestCandidate = cell;
                }
            }
        }

        if (bestCandidate != null && bestCandidate != currentPlayingCell) {
            pauseCurrent();
            if (bestCandidate.canAutoplayVideo()) {
                currentPlayingCell = bestCandidate;
                currentPlayingCell.startAutoplay();
            }
        } else if (bestCandidate == null) {
            pauseCurrent();
        }
    }

    public void pauseCurrent() {
        if (currentPlayingCell != null) {
            currentPlayingCell.stopAutoplay();
            currentPlayingCell = null;
        }
    }

    public void onDestroy() {
        pauseCurrent();
    }

    private int getVisibleArea(View view) {
        if (view == null || listView == null) return 0;
        int rvTop = this.listView.getTop();
        int rvBottom = this.listView.getBottom();

        int viewTop = view.getTop() + ((View) view.getParent()).getTop();
        int viewBottom = viewTop + view.getHeight();

        int visibleTop = Math.max(viewTop, rvTop);
        int visibleBottom = Math.min(viewBottom, rvBottom);

        if (visibleBottom <= visibleTop) return 0;
        return visibleBottom - visibleTop;
    }
}