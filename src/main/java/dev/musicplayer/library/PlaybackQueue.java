package dev.musicplayer.library;
import java.util.*;
public final class PlaybackQueue{
 private final List<Track> items=new ArrayList<>();private int index=-1;private boolean shuffle;private String repeat="all";
 public void set(List<Track> list,int current,boolean shuffle,String repeat){items.clear();items.addAll(list);index=Math.max(-1,Math.min(current,items.size()-1));this.shuffle=shuffle;this.repeat=repeat;}
 public void playNow(Track t){int i=items.indexOf(t);if(i<0){items.add(t);i=items.size()-1;}index=i;}
 public void add(Track t){if(t!=null&&!items.contains(t))items.add(t);}
 public void addNext(Track t){if(t==null)return;items.remove(t);int at=Math.min(index+1,items.size());items.add(at,t);}
 public Track next(){if(items.isEmpty())return null;if(shuffle&&items.size()>1){List<Integer> candidates=new ArrayList<>();for(int i=0;i<items.size();i++)if(i!=index)candidates.add(i);index=candidates.get(new Random().nextInt(candidates.size()));return items.get(index);}if(index+1<items.size()){index++;return items.get(index);}if("one".equals(repeat)){return items.get(index);}if("all".equals(repeat)){index=0;return items.get(index);}return null;}
 public Track previous(){if(items.isEmpty())return null;if(index>0){index--;return items.get(index);}if("all".equals(repeat)){index=items.size()-1;return items.get(index);}return items.get(Math.max(0,index));}
 public void remove(int i){if(i<0||i>=items.size())return;items.remove(i);if(i<index)index--;else if(index>=items.size())index=items.size()-1;}
 public void move(int from,int to){if(from<0||to<0||from>=items.size()||to>=items.size()||from==to)return;int oldIndex=index;Track t=items.remove(from);items.add(to,t);if(oldIndex==from)index=to;else if(from<oldIndex&&to>=oldIndex)index=oldIndex-1;else if(from>oldIndex&&to<=oldIndex)index=oldIndex+1;else index=oldIndex;}
 public void moveTo(int from,int to){if(items.isEmpty())return;to=Math.max(0,Math.min(to,items.size()-1));move(from,to);}
 public void moveCurrentToFront(){if(index<=0||index>=items.size())return;Track t=items.remove(index);items.add(0,t);index=0;}
 public int removeDuplicates(){int removed=0;Set<String> seen=new HashSet<>();for(int i=items.size()-1;i>=0;i--){String k=items.get(i).key();if(!seen.add(k)){if(i<index)index--;items.remove(i);removed++;}}if(index>=items.size())index=items.size()-1;return removed;}
 public int removeBeforeCurrent(){if(index<=0)return 0;int n=index;items.subList(0,index).clear();index=0;return n;}
 public void clear(){items.clear();index=-1;} public List<Track> items(){return List.copyOf(items);}public int index(){return index;}public boolean shuffle(){return shuffle;}public void shuffle(boolean x){shuffle=x;}public String repeat(){return repeat;}public void repeat(String x){repeat=x==null?"all":x;}
}
