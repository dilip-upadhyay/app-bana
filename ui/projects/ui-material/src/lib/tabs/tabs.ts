import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';

export interface AbTabItem {
  label: string;
  content?: string;
}

@Component({
  selector: 'ab-tabs',
  standalone: true,
  imports: [CommonModule, MatTabsModule],
  templateUrl: './tabs.html',
  styleUrl: './tabs.css'
})
export class Tabs {
  @Input() selectedIndex = 0;
  @Input() tabs: AbTabItem[] = [];
  @Output() selectedIndexChange = new EventEmitter<number>();
}
